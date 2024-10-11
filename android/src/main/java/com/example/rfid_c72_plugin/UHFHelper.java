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
            public void handleMessage(Message msg) {
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
        if (barcodeDecoder == null) {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
        }
        barcodeDecoder.open(context);

        //BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);

        barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
            @Override
            public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                Log.e(TAG,"BarcodeDecoder==========================:"+barcodeEntity.getResultCode());
                if(barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS){
                    scannedBarcode = barcodeEntity.getBarcodeData();
                    Log.e(TAG,"Data==========================:"+barcodeEntity.getBarcodeData());
                }else{
                    scannedBarcode = "FAIL";
                }
            }
        });
        return true;
    }


    public boolean scanBarcode() {
        barcodeDecoder.startScan();
        Log.i(TAG, "Calling scan code");
        return true;
    }

    public boolean stopScanBarcode() {
        barcodeDecoder.stopScan();
        Log.i(TAG, "Calling stop scan");
        return true;
    }

    private boolean startRfidRead(boolean performSingleRead) {
        if (continuousRfidReadActive) {
            return true; // RFID read is already active
        }
    
        if (performSingleRead) {
            // Single read
            UHFTAGInfo tagInfo = mReader.inventorySingleTag();
            if (tagInfo != null) {
                String epc = tagInfo.getEPC();
                addEPCToList(epc, tagInfo.getRssi());
                return true;
            }
        } else {
            // Continuous read
            if (mReader.startInventoryTag()) {
                continuousRfidReadActive = true;
                new RfidContinuousReadThread().start();
                return true;
            }
        }
        return false;
    }
    
    public boolean startRfidSingle() {
        return startRfidRead(true);  // Calls the private method to handle single read
    }
    
    public boolean startRfidContinuous() {
        // Auto read multi  .startInventoryTag((byte) 0, (byte) 0))
        // mContext.mReader.setEPCTIDMode(true);
        return startRfidRead(false);  // Calls the private method to handle continuous read
    }

    public boolean startBarcodeContinuous() {
        return true;
    }

    public void clearData() {
        scannedBarcode = null;
        tagList.clear();
    }

    public boolean stopRfid() {
        if (continuousRfidReadActive && mReader != null) {
            continuousRfidReadActive = false;
            return mReader.stopInventory();
        }
        continuousRfidReadActive = false;
        clearData();
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

    public boolean closeScanBarcode() {
        barcodeDecoder.close();
        return true;
    }

    class RfidContinuousReadThread extends Thread {
        public void run() {
            String strTid;
            String strResult;
            UHFTAGInfo res = null;
            while (continuousRfidReadActive) {
                res = mReader.readTagFromBuffer();
                if (res != null) {
                    strTid = res.getTid();
                    if (strTid.length() != 0 && !strTid.equals("0000000" +
                            "000000000") && !strTid.equals("000000000000000000000000")) {
                        strResult = "TID:" + strTid + "\n";
                    } else {
                        strResult = "";
                    }
                    Log.i("data", "c" + res.getEPC() + "|" + strResult);
                    Message msg = rfidHandler.obtainMessage();
                    msg.obj = strResult + "EPC:" + res.getEPC() + "@" + res.getRssi();

                    rfidHandler.sendMessage(msg);
                }
            }
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
    
            //BarcodeUtility.getInstance().enablePlaySuccessSound(context, true);
    
            barcodeDecoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
                @Override
                public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                    Log.e(TAG,"BarcodeDecoder==========================:"+barcodeEntity.getResultCode());
                    if(barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS){
                        // scannedBarcode = barcodeEntity.getBarcodeData();
                        Message msg = rfidHandler.obtainMessage();
                        msg.obj = barcodeEntity.getBarcodeData();
                        barcodeHandler.sendMessage(msg);
                        Log.e(TAG,"Data==========================:"+barcodeEntity.getBarcodeData());
                    } else {
                        // scannedBarcode = "FAIL";
                        Log.e(TAG, "ERROR: failed to scan");
                    }
                }
            });
            while (continuousBarcodeReadActive) {

                // res = mReader.readTagFromBuffer();
                // if (res != null) {
                //     strTid = res.getTid();
                //     if (strTid.length() != 0 && !strTid.equals("0000000" +
                //             "000000000") && !strTid.equals("000000000000000000000000")) {
                //         strResult = "TID:" + strTid + "\n";
                //     } else {
                //         strResult = "";
                //     }
                //     Log.i("data", "c" + res.getEPC() + "|" + strResult);
                //     Message msg = rfidHandler.obtainMessage();
                //     msg.obj = strResult + "EPC:" + res.getEPC() + "@" + res.getRssi();

                //     rfidHandler.sendMessage(msg);
                // }
            }

            barcodeDecoder.close();
        }
    }

}
