package com.example.rfid_c72_plugin;

import android.content.Context;

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
import io.flutter.plugin.common.PluginRegistry.Registrar;

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
  private static PublishSubject<Boolean> connectedStatusSubject = PublishSubject.create();
  private static PublishSubject<String> tagsStatusSubject = PublishSubject.create();
  private static PublishSubject<String> barcodeScanSubject = PublishSubject.create();

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "rfid_c72_plugin");
    initConnectedEvent(registrar.messenger());
    initRfidReadEvent(registrar.messenger());
    initBarcodeReadEvent(registrar.messenger());
    channel.setMethodCallHandler(new RfidC72Plugin());

    UHFHelper.getInstance().init(registrar.context());

    // This feeds the RFID reads into the stream
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
    });
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "rfid_c72_plugin");
    initConnectedEvent(flutterPluginBinding.getBinaryMessenger());
    initRfidReadEvent(flutterPluginBinding.getBinaryMessenger());
    initBarcodeReadEvent(flutterPluginBinding.getBinaryMessenger());
    Context applicationContext = flutterPluginBinding.getApplicationContext();
    channel.setMethodCallHandler(new RfidC72Plugin());
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
      default:
        result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
  }

}
