package com.example.rfid_c72_plugin;

import android.content.Context;
import android.util.Log;
import java.util.Map;


import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;


/**
 * RfidC72Plugin
 */
public class RfidC72Plugin implements FlutterPlugin, MethodCallHandler {
  private static final String CHANNEL_isContinuousRfidReadActive = "isContinuousRfidReadActive";
  private static final String CHANNEL_startRfidSingle = "startRfidSingle";
  private static final String CHANNEL_startRfidContinuous = "startRfidContinuous";
  private static final String CHANNEL_startBarcodeContinuous = "startBarcodeContinuous";
  private static final String CHANNEL_stopRfid = "stopRfid";
  private static final String CHANNEL_clearData = "clearData";
  private static final String CHANNEL_isEmptyTags = "isEmptyTags";
  private static final String CHANNEL_closeRfidReader = "closeRfidReader";
  private static final String CHANNEL_connectRfid = "connectRfid";
  private static final String CHANNEL_isRfidConnected = "isRfidConnected";
  private static final String CHANNEL_setPowerLevel = "setPowerLevel";
  private static final String CHANNEL_setWorkArea = "setWorkArea";
  private static final String CHANNEL_connectedStatusSubject = "connectedStatusSubject";
  private static final String CHANNEL_tagsStatusSubject = "tagsStatusSubject";
  private static final String CHANNEL_barcodeScanSubject = "barcodeScanSubject";
  private static final String CHANNEL_connectBarcode = "connectBarcode";
  private static final String CHANNEL_scanBarcode = "scanBarcode";
  private static final String CHANNEL_stopScanBarcode = "stopScanBarcode";
  private static final String CHANNEL_readBarcode = "readBarcode";
  private static final String CHANNEL_closeScanBarcode="closeScanBarcode";

  private static final String CHANNEL_locationValueSubject = "locationValueSubject";

  private static final String CHANNEL_startTagLocation = "startTagLocation";
  private static final String CHANNEL_stopTagLocation = "stopTagLocation";
  private static final String CHANNEL_isLocationRunning = "isLocationRunning";
  private static final String CHANNEL_setLocationDynamicDistance = "setLocationDynamicDistance";

  private static PublishSubject<Boolean> connectedStatusSubject = PublishSubject.create();
  private static PublishSubject<String> tagsStatusSubject = PublishSubject.create();
  private static PublishSubject<String> barcodeScanSubject = PublishSubject.create();

  private static PublishSubject<Map<String, Object>> locationValueSubject = PublishSubject.create();

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    BinaryMessenger messenger = binding.getBinaryMessenger();
    final MethodChannel channel = new MethodChannel(messenger, "rfid_c72_plugin");
    channel.setMethodCallHandler(new RfidC72Plugin());
    // Initialize your event channels and listeners
    initConnectedEvent(messenger);
    initRfidReadEvent(messenger);
    initBarcodeReadEvent(messenger);
    initLocationValueEvent(messenger);


    Context applicationContext = binding.getApplicationContext();
    UHFHelper.getInstance().init(applicationContext);
    UHFHelper.getInstance().setUhfListener(new UHFListener() {
      @Override
      public void onRfidRead(String tagsJson) {
        if (tagsJson != null)
          tagsStatusSubject.onNext(tagsJson);
      }
      @Override
      public void onBarcodeRead(String barcodeScan) {
        if (barcodeScan != null)
          barcodeScanSubject.onNext(barcodeScan);
      }
      @Override
      public void onRfidConnect(boolean isRfidConnected, int powerLevel) {
        connectedStatusSubject.onNext(isRfidConnected);
      }


      @Override
      public void onLocationValue(int value, boolean valid) {
        locationValueSubject.onNext(new LocationData(value, valid).toMap());
      }


    });
  }


  private static void initConnectedEvent(BinaryMessenger messenger) {
    final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_connectedStatusSubject);
    scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
      @Override
      public void onListen(Object o, final EventChannel.EventSink eventSink) {
        connectedStatusSubject
          .subscribeOn(Schedulers.newThread())
          .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Boolean>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(Boolean isRfidConnected) {
              eventSink.success(isRfidConnected);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
          });
      }

      @Override
      public void onCancel(Object o) {

      }
    });
  }

  private static void initRfidReadEvent(BinaryMessenger messenger) {
    final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_tagsStatusSubject);
    scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
      @Override
      public void onListen(Object o, final EventChannel.EventSink eventSink) {
        tagsStatusSubject
          .subscribeOn(Schedulers.newThread())
          .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(String tag) {
              eventSink.success(tag);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
          });
      }

      @Override
      public void onCancel(Object o) {

      }
    });
  }

  private static void initBarcodeReadEvent(BinaryMessenger messenger) {
    final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_barcodeScanSubject);
    scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
      @Override
      public void onListen(Object o, final EventChannel.EventSink eventSink) {
        barcodeScanSubject
          .subscribeOn(Schedulers.newThread())
          .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(String value) {
              eventSink.success(value);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onComplete() {

            }
          });
      }

      @Override
      public void onCancel(Object o) {

      }
    });
  }


  private static void initLocationValueEvent(BinaryMessenger messenger) {
    final EventChannel locationEventChannel = new EventChannel(messenger, CHANNEL_locationValueSubject);
    locationEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
      @Override
      public void onListen(Object arguments, final EventChannel.EventSink eventSink) {
        Log.d("RfidC72Plugin", "Location event channel - onListen called");
        locationValueSubject
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Map<String, Object>>() {
                  @Override
                  public void onSubscribe(Disposable d) {
                    Log.d("RfidC72Plugin", "Location subscription started");
                  }

                  @Override
                  public void onNext(Map<String, Object> locationDataMap) {
                    try {
                      eventSink.success(locationDataMap);
                    } catch (Exception e) {
                      Log.e("RfidC72Plugin", "Error sending location data to Flutter", e);
                    }
                  }

                  @Override
                  public void onError(Throwable e) {
                    Log.e("RfidC72Plugin", "Location stream error", e);
                  }

                  @Override
                  public void onComplete() {
                    Log.d("RfidC72Plugin", "Location stream completed");
                  }
                });
      }

      @Override
      public void onCancel(Object arguments) {
        Log.d("RfidC72Plugin", "Location event channel - onCancel called");
      }
    });
  }



  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    handleMethods(call, result);
  }

  private void handleMethods(MethodCall call, Result result) {
    switch (call.method) {
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;
      case CHANNEL_isContinuousRfidReadActive:
        result.success(UHFHelper.getInstance().isContinuousRfidReadActive());
        break;
      case CHANNEL_startRfidSingle:
        result.success(UHFHelper.getInstance().startRfidSingle());
        break;
      case CHANNEL_startRfidContinuous:
        result.success(UHFHelper.getInstance().startRfidContinuous());
        break;
      case CHANNEL_startBarcodeContinuous:
        result.success(UHFHelper.getInstance().startBarcodeContinuous());
        break;
      case CHANNEL_stopRfid:
        result.success(UHFHelper.getInstance().stopRfid());
        break;
      case CHANNEL_clearData:
        UHFHelper.getInstance().clearData();
        result.success(true);
        break;
      case CHANNEL_isEmptyTags:
        result.success(UHFHelper.getInstance().isEmptyTags());
        break;
      case CHANNEL_closeRfidReader:
        UHFHelper.getInstance().closeRfidReader();
        result.success(true);
        break;
      case CHANNEL_connectRfid:
        result.success(UHFHelper.getInstance().connectRfid());
        break;
      case CHANNEL_isRfidConnected:
        result.success(UHFHelper.getInstance().isRfidConnected());
        break;
      case CHANNEL_connectBarcode:
        result.success(UHFHelper.getInstance().connectBarcode());
        break;
      case CHANNEL_scanBarcode:
        result.success(UHFHelper.getInstance().scanBarcode());
        break;
      case CHANNEL_stopScanBarcode:
        result.success(UHFHelper.getInstance().stopScanBarcode());
        break;
      case CHANNEL_closeScanBarcode:
        result.success(UHFHelper.getInstance().closeScanBarcode());
        break;
      case CHANNEL_setPowerLevel:
        String powerLevel = call.argument("value");
        result.success(UHFHelper.getInstance().setPowerLevel(powerLevel));
        break;
      case CHANNEL_setWorkArea:
        String workArea = call.argument("value");
        result.success(UHFHelper.getInstance().setWorkArea(workArea));
        break;
      case CHANNEL_readBarcode:
        result.success(UHFHelper.getInstance().readBarcode());
        break;
      case CHANNEL_startTagLocation:
        String epc = call.argument("epc");
        result.success(UHFHelper.getInstance().startTagLocation(epc));
        break;

      case CHANNEL_stopTagLocation:
        result.success(UHFHelper.getInstance().stopTagLocation());
        break;

      case CHANNEL_isLocationRunning:
        result.success(UHFHelper.getInstance().isLocationRunning());
        break;

      case CHANNEL_setLocationDynamicDistance:
        int distance = call.argument("value");
        result.success(UHFHelper.getInstance().setLocationDynamicDistance(distance));
        break;

      default:
        result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
  }

}
