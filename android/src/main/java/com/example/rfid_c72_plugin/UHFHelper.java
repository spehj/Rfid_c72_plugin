package com.example.rfid_c72_plugin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UHFHelper {
    private static final String TAG = "UHFHelper";
    private static final int MAX_TAG_CACHE_SIZE = 1000;

    // Adaptive batch update settings
    private static final int MIN_BATCH_INTERVAL_MS = 100;
    private static final int MAX_BATCH_INTERVAL_MS = 500;
    private static final int DEFAULT_BATCH_INTERVAL_MS = 200;
    private int currentBatchIntervalMs = DEFAULT_BATCH_INTERVAL_MS;

    // Object pool size
    private static final int OBJECT_POOL_SIZE = 50;

    private static UHFHelper instance;
    public RFIDWithUHFUART mReader;

    public BarcodeDecoder barcodeDecoder;
    private Handler rfidHandler;
    private Handler barcodeHandler;
    private UHFListener uhfListener;

    // Thread-safe flags
    private final AtomicBoolean continuousRfidReadActive = new AtomicBoolean(false);
    private final AtomicBoolean continuousBarcodeReadActive = new AtomicBoolean(false);
    private final AtomicBoolean isRfidConnected = new AtomicBoolean(false);
    private final AtomicBoolean isInventoryRunning = new AtomicBoolean(false);
    private final AtomicBoolean isBarcodeInitialized = new AtomicBoolean(false);
    private final AtomicBoolean pendingUpdates = new AtomicBoolean(false);

    // Tag rate tracking for adaptive updates
    private final AtomicInteger tagsInLastBatch = new AtomicInteger(0);

    // Use ConcurrentHashMap for thread safety
    private ConcurrentHashMap<String, EPC> tagList;
    private ConcurrentHashMap<String, EPC> newTagsBatch;
    private Set<String> changedTagsInBatch;

    // Thread pools
    private ScheduledExecutorService scheduler;
    private ExecutorService processingPool;

    // Object pool for EPC tags to reduce GC pressure
    private LruCache<String, EPC> tagObjectPool;

    // Last sent JSON to avoid duplicate updates
    private String lastSentJson = "";

    private String scannedBarcode;
    private Context context;

    private UHFHelper() {
        // Set up the object pool
        tagObjectPool = new LruCache<String, EPC>(OBJECT_POOL_SIZE) {
            @Override
            protected EPC create(String key) {
                return new EPC();
            }
        };
    }

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

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public void setUhfListener(UHFListener uhfListener) {
        this.uhfListener = uhfListener;
    }

    public void init(Context context) {
        this.context = context;
        tagList = new ConcurrentHashMap<>();
        newTagsBatch = new ConcurrentHashMap<>();
        changedTagsInBatch = new HashSet<>();

        // Create thread pools
        scheduler = Executors.newSingleThreadScheduledExecutor();
        processingPool = Executors.newFixedThreadPool(2); // Pool for processing tasks

        clearData();

        // Ensure handlers are created on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initHandlers();
        } else {
            new Handler(Looper.getMainLooper()).post(this::initHandlers);
        }

        // Schedule batch processing of tags with initial interval
        scheduleNextBatchUpdate();
    }

    private void scheduleNextBatchUpdate() {
        scheduler.schedule(() -> {
            processBatchUpdates();

            // Adaptive batch interval based on tag read rate
            int tagCount = tagsInLastBatch.getAndSet(0);

            // Adjust batch interval based on tag density
            if (tagCount > 50) {
                // Many tags - process more frequently
                currentBatchIntervalMs = Math.max(MIN_BATCH_INTERVAL_MS, currentBatchIntervalMs - 20);
            } else if (tagCount < 5) {
                // Few tags - process less frequently
                currentBatchIntervalMs = Math.min(MAX_BATCH_INTERVAL_MS, currentBatchIntervalMs + 20);
            }

            // Schedule next update with the new interval
            scheduleNextBatchUpdate();
        }, currentBatchIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void initHandlers() {
        rfidHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // Offload tag processing to background thread
                processingPool.execute(() -> {
                    String result = msg.obj + "";
                    String[] strs = result.split("@");
                    addEPCToList(strs[0], strs[1]);
                });
            }
        };

        barcodeHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                String result = msg.obj + "";
                recordBarcodeScan(result);
            }
        };
    }

    private void processBatchUpdates() {
        if (newTagsBatch.isEmpty() || !pendingUpdates.get()) {
            return;
        }

        // Reset the flag
        pendingUpdates.set(false);

        // Process batch in background thread
        processingPool.execute(() -> {
            // Add all new tags to the main list
            for (Map.Entry<String, EPC> entry : newTagsBatch.entrySet()) {
                String epc = entry.getKey();
                EPC tag = entry.getValue();

                if (tagList.containsKey(epc)) {
                    EPC existingTag = tagList.get(epc);
                    if (existingTag != null) {
                        int tagCount = Integer.parseInt(existingTag.getCount()) +
                                Integer.parseInt(tag.getCount());
                        existingTag.setCount(String.valueOf(tagCount));
                        existingTag.setRssi(tag.getRssi()); // Update with latest RSSI
                        changedTagsInBatch.add(epc);
                    }
                } else {
                    tagList.put(epc, tag);
                    changedTagsInBatch.add(epc);
                }
            }

            // Clear the batch and make tag objects available for reuse
            for (EPC tag : newTagsBatch.values()) {
                recycleTagObject(tag);
            }
            newTagsBatch.clear();

            // Enforce size limit on the main tag list if needed
            if (tagList.size() > MAX_TAG_CACHE_SIZE) {
                trimTagList();
            }

            // Only send update if there are changes
            if (!changedTagsInBatch.isEmpty()) {
                // Send only changed tags if there are fewer than 50% changed
                boolean sendFullUpdate = changedTagsInBatch.size() > tagList.size() * 0.5;
                sendTagListUpdateToListener(sendFullUpdate);
                changedTagsInBatch.clear();
            }
        });
    }

    private void recycleTagObject(EPC tag) {
        // Reset tag to initial state and return to pool
        if (tag != null) {
            tag.setId("");
            tag.setEpc("");
            tag.setCount("0");
            tag.setRssi("0");
            // Tag will be garbage collected or reused by the LruCache
        }
    }

    private EPC getTagFromPool() {
        // Get a tag from the pool or create a new one
        return tagObjectPool.get("tag");
    }

    private void trimTagList() {
        // Remove oldest entries (or least seen, depending on strategy)
        int toRemove = tagList.size() - MAX_TAG_CACHE_SIZE;

        // Find entries with lowest count
        Map.Entry<String, EPC>[] entries = tagList.entrySet().toArray(new Map.Entry[0]);
        Arrays.sort(entries, (a, b) -> {
            int countA = Integer.parseInt(a.getValue().getCount());
            int countB = Integer.parseInt(b.getValue().getCount());
            return Integer.compare(countA, countB);
        });

        // Remove the lowest count entries
        for (int i = 0; i < toRemove && i < entries.length; i++) {
            tagList.remove(entries[i].getKey());
        }
    }

    private void sendTagListUpdateToListener(boolean fullUpdate) {
        if (uhfListener == null) return;

        try {
            final JSONArray jsonArray = new JSONArray();

            // Choose which tags to send
            Set<String> tagsToSend = fullUpdate ? tagList.keySet() : changedTagsInBatch;

            for (String epc : tagsToSend) {
                EPC epcTag = tagList.get(epc);
                if (epcTag != null) {
                    JSONObject json = new JSONObject();
                    try {
                        json.put(TagKey.ID, Objects.requireNonNull(epcTag).getId());
                        json.put(TagKey.EPC, epcTag.getEpc());
                        json.put(TagKey.RSSI, epcTag.getRssi());
                        json.put(TagKey.COUNT, epcTag.getCount());
                        jsonArray.put(json);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating JSON object", e);
                    }
                }
            }

            // Check if this update is different from the last one
            final String jsonString = jsonArray.toString();
            if (jsonString.equals(lastSentJson) && !fullUpdate) {
                // Skip duplicate update
                return;
            }

            lastSentJson = jsonString;

            // Send on main thread
            new Handler(Looper.getMainLooper()).post(() ->
                    uhfListener.onRfidRead(jsonString));

        } catch (Exception e) {
            Log.e(TAG, "Error sending tag updates", e);
        }
    }

    public String readBarcode() {
        return scannedBarcode != null ? scannedBarcode : "FAIL";
    }

    public boolean connectRfid() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
        } catch (Exception ex) {
            Log.e(TAG, "Error getting RFID instance", ex);
            notifyRfidConnect(false, 0);
            return false;
        }

        if (mReader != null) {
            boolean connected = mReader.init(context);
            isRfidConnected.set(connected);

            if (connected) {
                // Optimize reader settings for performance
                try {
                    // Set maximum performance mode - adjust if battery life is an issue
                    mReader.setPower(30); // Maximum power
                    // Set faster inventory mode if available
                    // Check device documentation for any available fast scan modes
                }
                catch (Exception e) {
                    Log.e(TAG, "Error setting reader performance options", e);
                }
            }

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
        Log.d(TAG, "connectBarcode");
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
                            Log.d(TAG, "Scanned data: " + scannedBarcode);
                        } else {
                            Message msg = barcodeHandler.obtainMessage();
                            msg.obj = "-1";
                            barcodeHandler.sendMessage(msg);
                            Log.d(TAG, "Scan failed with code: " + barcodeEntity.getResultCode());
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
        Log.d(TAG, "Starting barcode scan");
        return true;
    }

    public boolean stopScanBarcode() {
        if (!isBarcodeInitialized.get()) {
            return false;
        }
        Log.d(TAG, "Stopping barcode scan");
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
            String epc = tagInfo.getEPC();
            addEPCToList(epc, tagInfo.getRssi());
            return true;
        }
        return false;
    }

    public boolean startRfidContinuous() {
        if (continuousRfidReadActive.get() || isInventoryRunning.get()) {
            Log.d(TAG, "Continuous read already active");
            return true; // Already running
        }

        if (mReader != null) {
            isInventoryRunning.set(true);
            continuousRfidReadActive.set(true);

            // Reset the batch interval to default when starting a new inventory
            currentBatchIntervalMs = DEFAULT_BATCH_INTERVAL_MS;

            // Clear existing data for a fresh start
            newTagsBatch.clear();
            changedTagsInBatch.clear();

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
        tagList.clear();
        newTagsBatch.clear();
        changedTagsInBatch.clear();
        lastSentJson = "";
    }

    public boolean stopRfid() {
        if (mReader != null) {
            continuousRfidReadActive.set(false);
            isInventoryRunning.set(false);

            // Process any remaining tags in the batch immediately
            processBatchUpdates();

            mReader.stopInventory();
            mReader.setInventoryCallback(null);
            Log.d(TAG, "Stopped RFID reading");
            return true;
        }
        return false;
    }

    public void closeRfidReader() {
        // Stop all operations first
        continuousRfidReadActive.set(false);
        if (mReader != null) {
            mReader.free();
            isRfidConnected.set(false);
        }

        // Shut down thread pools properly
        shutdownThreadPools();

        clearData();
    }

    private void shutdownThreadPools() {
        // Proper shutdown sequence for thread pools
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // Force immediate shutdown
            try {
                if (!scheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Scheduler didn't terminate in time");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Scheduler shutdown interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        if (processingPool != null && !processingPool.isShutdown()) {
            processingPool.shutdownNow(); // Force immediate shutdown
            try {
                if (!processingPool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Processing pool didn't terminate in time");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Processing pool shutdown interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
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

    private void addEPCToList(String epc, String rssi) {
        if (TextUtils.isEmpty(epc)) {
            return;
        }

        // Increment tag count for the current batch
        tagsInLastBatch.incrementAndGet();

        // Get tag from object pool instead of creating new object
        EPC tag = getTagFromPool();
        tag.setId("");
        tag.setEpc(epc);
        tag.setCount("1");
        tag.setRssi(rssi);

        // Update count if this tag is already in the current batch
        if (newTagsBatch.containsKey(epc)) {
            EPC existingTag = newTagsBatch.get(epc);
            if (existingTag != null) {
                int tagCount = Integer.parseInt(existingTag.getCount()) + 1;
                existingTag.setCount(String.valueOf(tagCount));
                existingTag.setRssi(rssi); // Update with latest RSSI

                // Return the unused tag to the pool
                recycleTagObject(tag);
            }
        } else {
            newTagsBatch.put(epc, tag);
        }

        // Mark that we have updates to process
        pendingUpdates.set(true);
    }

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

    class RfidContinuousReadThread extends Thread {
        @Override
        public void run() {
            // Set thread priority to improve performance
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            mReader.setInventoryCallback(new IUHFInventoryCallback() {
                @Override
                public void callback(UHFTAGInfo uhftagInfo) {
                    if (uhftagInfo != null) {
                        String strTid = uhftagInfo.getTid();
                        String strResult = "";

                        if (!TextUtils.isEmpty(strTid) && !strTid.equals("0000000000000000")
                                && !strTid.equals("000000000000000000000000")) {
                            strResult = "TID:" + strTid + "\n";
                        }

                        String tagData = strResult + "EPC:" + uhftagInfo.getEPC() + "@" + uhftagInfo.getRssi();
                        rfidHandler.obtainMessage(1, tagData).sendToTarget();
                    }
                }
            });

            boolean startSuccess = mReader.startInventoryTag();
            Log.d(TAG, "Started inventory: " + startSuccess);

            // More efficient polling loop with proper sleep
            while (continuousRfidReadActive.get() && isInventoryRunning.get()) {
                try {
                    Thread.sleep(20); // Reduced sleep time for faster response
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            mReader.stopInventory();
            Log.d(TAG, "Inventory thread stopped");
        }
    }

    class BarcodeContinuousReadThread extends Thread {
        @Override
        public void run() {
            // Set thread priority
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);

            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            }
            barcodeDecoder.open(context);

            Log.d(TAG, "Initialized barcode thread");

            BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);

            barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
                @Override
                public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                    if(barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS) {
                        Message msg = barcodeHandler.obtainMessage();
                        msg.obj = barcodeEntity.getBarcodeData();
                        barcodeHandler.sendMessage(msg);
                        Log.d(TAG, "Data: " + barcodeEntity.getBarcodeData());
                    } else {
                        Log.d(TAG, "Failed to scan barcode");
                    }
                }
            });

            // More efficient wait loop
            while (continuousBarcodeReadActive.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            barcodeDecoder.close();
        }
    }
}