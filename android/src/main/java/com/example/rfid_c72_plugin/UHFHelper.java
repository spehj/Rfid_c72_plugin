package com.example.rfid_c72_plugin;

import android.content.Context;
import android.os.Handler;
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

import java.util.HashMap;
import java.util.Objects;

public class UHFHelper {
    private static UHFHelper instance;
    public RFIDWithUHFUART mReader;

    String TAG="MainActivity_2D";

    public BarcodeDecoder barcodeDecoder;
    Handler rfidHandler;
    Handler barcodeHandler;
    private UHFListener uhfListener;
    private boolean continuousRfidReadActive = false;
    private boolean continuousBarcodeReadActive = false;
    private boolean isRfidConnected = false;
    private boolean isInventoryRunning = false;

    private boolean isBarcodeInitialized = false;

    //private boolean performSingleRead = false;
    private HashMap<String, EPC> tagList;

    private String scannedBarcode;

    private Context context;

    private UHFHelper() {
    }

    public static UHFHelper getInstance() {
        if (instance == null)
            instance = new UHFHelper();
        return instance;
    }

    //public RFIDWithUHFUART getReader() {
    //   return mReader;
    //}

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public void setUhfListener(UHFListener uhfListener) {
        this.uhfListener = uhfListener;
    }

    public void init(Context context) {
        this.context = context;
        //this.uhfListener = uhfListener;
        tagList = new HashMap<String, EPC>();

        clearData();

        rfidHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String result = msg.obj + "";
                String[] strs = result.split("@");
                addEPCToList(strs[0], strs[1]);
            }
        };


        barcodeHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.e(TAG, "Received barcode message");
                String result = msg.obj + "";
                recordBarcodeScan(result);
            }
        };
    }

    public String readBarcode(){
        if(scannedBarcode != null) {
            return scannedBarcode;
        }else{
            return "FAIL";
        }
    }

    public boolean connectRfid() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
        } catch (Exception ex) {
            uhfListener.onRfidConnect(false, 0);
            return false;
        }
        if (mReader != null) {
            isRfidConnected = mReader.init(context);
            //mReader.setFrequencyMode(2);
            //mReader.setPower(29);
            uhfListener.onRfidConnect(isRfidConnected, 0);
            return isRfidConnected;
        }
        uhfListener.onRfidConnect(false, 0);
        return false;
    }



    public boolean connectBarcode() {
        Log.e(TAG, "connectBarcode");
        try {
            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            }
            boolean success = barcodeDecoder.open(context);
            if (success) {
                isBarcodeInitialized = true;
                BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);

                barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
                    @Override
                    public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                        Log.e(TAG,"BarcodeDecoder result: " + barcodeEntity.getResultCode());
                        if(barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS){
                            scannedBarcode = barcodeEntity.getBarcodeData();
                            Message msg = barcodeHandler.obtainMessage();
                            msg.obj = scannedBarcode;
                            barcodeHandler.sendMessage(msg);
                            Log.e(TAG,"Scanned data: " + scannedBarcode);
                        } else {
                            Message msg = barcodeHandler.obtainMessage();
                            msg.obj = "-1";
                            barcodeHandler.sendMessage(msg);
                            Log.e(TAG, "Scan failed with code: " + barcodeEntity.getResultCode());
                        }
                    }
                });
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing barcode scanner: " + e.getMessage());
            return false;
        }
    }



    public boolean scanBarcode() {
        if (!isBarcodeInitialized) {
            Log.e(TAG, "Barcode scanner not initialized");
            return false;
        }
        barcodeDecoder.startScan();
        Log.i(TAG, "Starting barcode scan");
        return true;
    }

    public boolean stopScanBarcode() {
        if (!isBarcodeInitialized) {
            return false;
        }
        Log.i(TAG, "Stopping barcode scan");
        barcodeDecoder.stopScan();
        return true;
    }

    public boolean closeScanBarcode() {
        if (barcodeDecoder != null) {
            barcodeDecoder.close();
            isBarcodeInitialized = false;
            continuousBarcodeReadActive = false;
            return true;
        }
        return false;
    }


    public boolean startRfidSingle() {
        if (continuousRfidReadActive || isInventoryRunning) {
            Log.e("RFID", "Cannot perform single read while continuous read is active");
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
        if (continuousRfidReadActive || isInventoryRunning) {
            Log.e("RFID", "Continuous read already active");
            return true; // Already running
        }

        if (mReader != null) {
            isInventoryRunning = true;
            continuousRfidReadActive = true;
            new RfidContinuousReadThread().start();
            return true;
        }

        Log.e("RFID", "mReader is null");
        return false;
    }


    public boolean startBarcodeContinuous() {
        continuousBarcodeReadActive = true;
        new BarcodeContinuousReadThread().start();
        return true;
    }

    public void clearData() {
        scannedBarcode = null;
        tagList.clear();
    }

    public boolean stopRfid() {
        if (mReader != null) {
            continuousRfidReadActive = false;
            isInventoryRunning = false;
            mReader.stopInventory();
            mReader.setInventoryCallback(null);
            Log.i("RFID", "Stopped RFID reading");
            return true;
        }
        return false;
    }


    public void closeRfidReader() {
        continuousRfidReadActive = false;
        if (mReader != null) {
            mReader.free();
            isRfidConnected = false;
        }
        clearData();
    }

    public boolean setPowerLevel(String level) {
        //5 dBm : 30 dBm
        if (mReader != null) {
            return mReader.setPower(Integer.parseInt(level));
        }
        return false;
    }

    public boolean setWorkArea(String area) {
        //China Area 920~925MHz
        //Chin2a Area 840~845MHz
        //ETSI Area 865~868MHz
        //Fixed Area 915MHz
        //United States Area 902~928MHz
        //{ "1", "2" 4", "8", "22", "50", "51", "52", "128"}
        if (mReader != null)
            return mReader.setFrequencyMode(Integer.parseInt(area));
        return false;
    }

    private void addEPCToList(String epc, String rssi) {
        if (!TextUtils.isEmpty(epc)) {
            EPC tag = new EPC();

            tag.setId("");
            tag.setEpc(epc);
            tag.setCount(String.valueOf(1));
            tag.setRssi(rssi);

            if (tagList.containsKey(epc)) {
                int tagCount = Integer.parseInt(Objects.requireNonNull(tagList.get(epc)).getCount()) + 1;
                tag.setCount(String.valueOf(tagCount));
            }
            tagList.put(epc, tag);

            final JSONArray jsonArray = new JSONArray();

            for (EPC epcTag : tagList.values()) {
                JSONObject json = new JSONObject();
                try {
                    json.put(TagKey.ID, Objects.requireNonNull(epcTag).getId());
                    json.put(TagKey.EPC, epcTag.getEpc());
                    json.put(TagKey.RSSI, epcTag.getRssi());
                    json.put(TagKey.COUNT, epcTag.getCount());
                    jsonArray.put(json);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            uhfListener.onRfidRead(jsonArray.toString());
        }
    }

    private void recordBarcodeScan(String barcodeScan) {
        uhfListener.onBarcodeRead(barcodeScan);
    }

    public boolean isEmptyTags() {
        return tagList != null && !tagList.isEmpty();
    }

    public boolean isContinuousRfidReadActive() {
        return continuousRfidReadActive;
    }

    public boolean isRfidConnected() {
        return isRfidConnected;
    }


    class RfidContinuousReadThread extends Thread {
        public void run() {
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
                        Log.i("RFID", "Tag read: " + tagData);
                        rfidHandler.obtainMessage(1, tagData).sendToTarget();
                    }
                }
            });

            boolean startSuccess = mReader.startInventoryTag();
            Log.i("RFID", "Started inventory: " + startSuccess);

            while (continuousRfidReadActive && isInventoryRunning) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            mReader.stopInventory();
            Log.i("RFID", "Inventory thread stopped");
        }
    }



    class BarcodeContinuousReadThread extends Thread {
        public void run() {
            String strTid;
            String strResult;
            UHFTAGInfo res = null;
            if (barcodeDecoder == null) {
                barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            }
            barcodeDecoder.open(context);

            Log.e(TAG, "Initialized barcode thread");
    
            BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);
    
            barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
                @Override
                public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                    Log.e(TAG,"BarcodeDecoder==========================:"+barcodeEntity.getResultCode());
                    if(barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS){
                        Message msg = barcodeHandler.obtainMessage();
                        msg.obj = barcodeEntity.getBarcodeData();
                        barcodeHandler.sendMessage(msg);
                        Log.e(TAG,"Data==========================:"+barcodeEntity.getBarcodeData());
                    } else {
                        // scannedBarcode = "FAIL";
                        Log.e(TAG, "ERROR: failed to scan");
                    }
                }
            });
            while (continuousBarcodeReadActive) {}

            barcodeDecoder.close();
        }
    }

}
