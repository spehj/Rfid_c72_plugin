package com.example.rfid_c72_plugin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.barcode.BarcodeUtility;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.BarcodeEntity;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Improved UHFHelper class.
 *
 * This version batches tag updates from the RFID reader so that updates (including RSSI changes)
 * are processed and sent to Flutter in one go rather than per tag. It still maintains duplicate
 * information on the native side to update RSSI values and counts.
 */
public class UHFHelper {
    private static final String TAG = "UHFHelper";
    private static final int MAX_TAG_CACHE_SIZE = 1000;  // Prevent memory issues with too many tags
    private static final int BATCH_UPDATE_INTERVAL_MS = 200; // Batch update interval

    private static UHFHelper instance;

    private RFIDWithUHFUART mReader;
    private BarcodeDecoder barcodeDecoder;
    private Handler rfidHandler;
    private Handler barcodeHandler;
    private UHFListener uhfListener;
    private Context context;

    // Atomic flags for thread safety
    private final AtomicBoolean continuousRfidReadActive = new AtomicBoolean(false);
    private final AtomicBoolean continuousBarcodeReadActive = new AtomicBoolean(false);
    private final AtomicBoolean isRfidConnected = new AtomicBoolean(false);
    private final AtomicBoolean isInventoryRunning = new AtomicBoolean(false);
    private final AtomicBoolean isBarcodeInitialized = new AtomicBoolean(false);
    private final AtomicBoolean pendingUpdates = new AtomicBoolean(false);

    // Thread-safe maps for maintaining the tag list and a batch of new tag updates
    private ConcurrentHashMap<String, EPC> tagList;
    private ConcurrentHashMap<String, EPC> newTagsBatch;

    private String scannedBarcode;

    // Scheduler to process batched tag updates
    private ScheduledExecutorService scheduler;

    // Private constructor (singleton)
    private UHFHelper() { }

    public static UHFHelper getInstance() {
        if (instance == null) {
            synchronized (UHFHelper.class) {
                if (instance == null) {
                    instance = new UHFHelper();
                }
            }
        }
        return instance;
    }

    public void setUhfListener(UHFListener listener) {
        this.uhfListener = listener;
    }

    public void init(Context context) {
        this.context = context;
        tagList = new ConcurrentHashMap<>();
        newTagsBatch = new ConcurrentHashMap<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        clearData();

        // Create handlers on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initHandlers();
        } else {
            new Handler(Looper.getMainLooper()).post(this::initHandlers);
        }

        // Schedule the batch update processor
        scheduler.scheduleAtFixedRate(this::processBatchUpdates, BATCH_UPDATE_INTERVAL_MS,
                BATCH_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void initHandlers() {
        rfidHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                String result = msg.obj.toString();
                // Expected format: EPC@RSSI
                String[] parts = result.split("@");
                if (parts.length >= 2) {
                    addEPCToBatch(parts[0], parts[1]);
                }
            }
        };

        barcodeHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                String result = msg.obj.toString();
                recordBarcodeScan(result);
            }
        };
    }

    /**
     * Process all batched tag updates. If a tag already exists, update its count and RSSI.
     * This minimizes the number of updates sent over the platform channel.
     */
    private void processBatchUpdates() {
        if (newTagsBatch.isEmpty() || !pendingUpdates.get()) {
            return;
        }
        pendingUpdates.set(false);

        // Merge new tags into the main tag list
        for (Map.Entry<String, EPC> entry : newTagsBatch.entrySet()) {
            String epc = entry.getKey();
            EPC newTag = entry.getValue();

            if (tagList.containsKey(epc)) {
                EPC existingTag = tagList.get(epc);
                if (existingTag != null) {
                    int count = Integer.parseInt(existingTag.getCount()) + Integer.parseInt(newTag.getCount());
                    existingTag.setCount(String.valueOf(count));
                    existingTag.setRssi(newTag.getRssi());
                }
            } else {
                tagList.put(epc, newTag);
            }
        }
        newTagsBatch.clear();

        if (tagList.size() > MAX_TAG_CACHE_SIZE) {
            trimTagList();
        }

        sendTagListUpdateToListener();
    }

    /**
     * Removes the oldest tags when the list exceeds MAX_TAG_CACHE_SIZE.
     */
    private void trimTagList() {
        int toRemove = tagList.size() - MAX_TAG_CACHE_SIZE;
        int removed = 0;
        for (String key : tagList.keySet()) {
            tagList.remove(key);
            removed++;
            if (removed >= toRemove) break;
        }
    }

    /**
     * Creates a JSON array of the current tags and sends it to the Flutter listener.
     */
    private void sendTagListUpdateToListener() {
        if (uhfListener == null) return;

        StringBuilder jsonBuilder = new StringBuilder("[");
        boolean first = true;

        for (EPC epcTag : tagList.values()) {
            if (!first) {
                jsonBuilder.append(",");
            } else {
                first = false;
            }

            jsonBuilder.append("{\"")
                    .append(TagKey.ID).append("\":\"").append(epcTag.getId()).append("\",\"")
                    .append(TagKey.EPC).append("\":\"").append(epcTag.getEpc()).append("\",\"")
                    .append(TagKey.RSSI).append("\":\"").append(epcTag.getRssi()).append("\",\"")
                    .append(TagKey.COUNT).append("\":\"").append(epcTag.getCount()).append("\"}");
        }

        jsonBuilder.append("]");
        final String jsonString = jsonBuilder.toString();

        new Handler(Looper.getMainLooper()).post(() ->
                uhfListener.onRfidRead(jsonString));
    }

    public String readBarcode() {
        return scannedBarcode != null ? scannedBarcode : "FAIL";
    }

    public boolean connectRfid() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
        } catch (Exception ex) {
            Log.e(TAG, "Error obtaining RFID instance", ex);
            notifyRfidConnect(false, 0);
            return false;
        }
        if (mReader != null) {
            boolean connected = mReader.init(context);
            isRfidConnected.set(connected);
            notifyRfidConnect(connected, 0);
            return connected;
        }
        notifyRfidConnect(false, 0);
        return false;
    }

    private void notifyRfidConnect(final boolean connected, final int code) {
        if (uhfListener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    uhfListener.onRfidConnect(connected, code));
        }
    }

    public boolean connectBarcode() {
        try {
            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            }
            boolean success = barcodeDecoder.open(context);
            if (success) {
                isBarcodeInitialized.set(true);
                BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);
                barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
                    @Override
                    public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                        if (barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS) {
                            scannedBarcode = barcodeEntity.getBarcodeData();
                            Message msg = barcodeHandler.obtainMessage();
                            msg.obj = scannedBarcode;
                            barcodeHandler.sendMessage(msg);
                        } else {
                            Message msg = barcodeHandler.obtainMessage();
                            msg.obj = "-1";
                            barcodeHandler.sendMessage(msg);
                        }
                    }
                });
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing barcode scanner", e);
            return false;
        }
    }

    public boolean scanBarcode() {
        if (!isBarcodeInitialized.get()) {
            Log.e(TAG, "Barcode scanner not initialized");
            return false;
        }
        barcodeDecoder.startScan();
        return true;
    }

    public boolean stopScanBarcode() {
        if (!isBarcodeInitialized.get()) {
            return false;
        }
        barcodeDecoder.stopScan();
        return true;
    }

    public boolean closeScanBarcode() {
        if (barcodeDecoder != null) {
            barcodeDecoder.close();
            isBarcodeInitialized.set(false);
            continuousBarcodeReadActive.set(false);
            return true;
        }
        return false;
    }

    public boolean startRfidSingle() {
        if (continuousRfidReadActive.get() || isInventoryRunning.get()) {
            Log.e(TAG, "Cannot perform single read while continuous read is active");
            return false;
        }
        UHFTAGInfo tagInfo = mReader.inventorySingleTag();
        if (tagInfo != null) {
            // Directly add to batch for processing
            addEPCToBatch(tagInfo.getEPC(), tagInfo.getRssi());
            return true;
        }
        return false;
    }

    public boolean startRfidContinuous() {
        if (continuousRfidReadActive.get() || isInventoryRunning.get()) {
            Log.e(TAG, "Continuous RFID read already active");
            return true;
        }
        if (mReader != null) {
            isInventoryRunning.set(true);
            continuousRfidReadActive.set(true);
            new RfidContinuousReadThread().start();
            return true;
        }
        Log.e(TAG, "mReader is null");
        return false;
    }

    public boolean startBarcodeContinuous() {
        continuousBarcodeReadActive.set(true);
        new BarcodeContinuousReadThread().start();
        return true;
    }

    public void clearData() {
        scannedBarcode = null;
        if (tagList != null) {
            tagList.clear();
        }
        if (newTagsBatch != null) {
            newTagsBatch.clear();
        }
    }

    public boolean stopRfid() {
        if (mReader != null) {
            continuousRfidReadActive.set(false);
            isInventoryRunning.set(false);
            mReader.stopInventory();
            mReader.setInventoryCallback(null);
            return true;
        }
        return false;
    }

    public void closeRfidReader() {
        continuousRfidReadActive.set(false);
        if (mReader != null) {
            mReader.free();
            isRfidConnected.set(false);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Scheduler termination interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
        clearData();
    }

    public boolean setPowerLevel(String level) {
        if (mReader != null) {
            return mReader.setPower(Integer.parseInt(level));
        }
        return false;
    }

    public boolean setWorkArea(String area) {
        if (mReader != null) {
            return mReader.setFrequencyMode(Integer.parseInt(area));
        }
        return false;
    }

    /**
     * Adds a new tag (or updates an existing one) into the batch.
     * The merge ensures that if the tag is already in the batch, its count is incremented and RSSI updated.
     */
    private void addEPCToBatch(String epc, String rssi) {
        if (TextUtils.isEmpty(epc)) return;

        EPC tag = new EPC();
        tag.setId("");
        tag.setEpc(epc);
        tag.setCount("1");
        tag.setRssi(rssi);

        newTagsBatch.merge(epc, tag, (existing, incoming) -> {
            int count = Integer.parseInt(existing.getCount()) + 1;
            existing.setCount(String.valueOf(count));
            existing.setRssi(incoming.getRssi());
            return existing;
        });
        pendingUpdates.set(true);
    }

    /**
     * Notifies the listener with the scanned barcode.
     */
    private void recordBarcodeScan(String barcodeScan) {
        if (uhfListener != null) {
            uhfListener.onBarcodeRead(barcodeScan);
        }
    }

    public boolean isEmptyTags() {
        return tagList != null && !tagList.isEmpty();
    }

    public boolean isContinuousRfidReadActive() {
        return continuousRfidReadActive.get();
    }

    public boolean isRfidConnected() {
        return isRfidConnected.get();
    }

    /**
     * Thread that continuously reads RFID tags.
     */
    class RfidContinuousReadThread extends Thread {
        @Override
        public void run() {
            mReader.setInventoryCallback(new IUHFInventoryCallback() {
                @Override
                public void callback(UHFTAGInfo uhftagInfo) {
                    if (uhftagInfo != null) {
                        String tid = uhftagInfo.getTid();
                        String prefix = "";
                        if (!TextUtils.isEmpty(tid) && !tid.equals("0000000000000000")
                                && !tid.equals("000000000000000000000000")) {
                            prefix = "TID:" + tid + "\n";
                        }
                        String tagData = prefix + "EPC:" + uhftagInfo.getEPC() + "@" + uhftagInfo.getRssi();
                        rfidHandler.obtainMessage(1, tagData).sendToTarget();
                    }
                }
            });
            boolean started = mReader.startInventoryTag();
            Log.d(TAG, "Started inventory: " + started);
            while (continuousRfidReadActive.get() && isInventoryRunning.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            mReader.stopInventory();
            Log.d(TAG, "Stopped inventory thread");
        }
    }

    /**
     * Thread that continuously reads barcodes.
     */
    class BarcodeContinuousReadThread extends Thread {
        @Override
        public void run() {
            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            }
            barcodeDecoder.open(context);
            BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);
            barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
                @Override
                public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                    if (barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS) {
                        Message msg = barcodeHandler.obtainMessage();
                        msg.obj = barcodeEntity.getBarcodeData();
                        barcodeHandler.sendMessage(msg);
                    } else {
                        Message msg = barcodeHandler.obtainMessage();
                        msg.obj = "-1";
                        barcodeHandler.sendMessage(msg);
                    }
                }
            });
            while (continuousBarcodeReadActive.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            barcodeDecoder.close();
        }
    }
}
