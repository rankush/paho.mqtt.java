/**
 * 
 */
package org.eclipse.paho.android.service.test;

import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;
import android.util.Log;

import org.eclipse.paho.android.service.MqttClientAndroidService;

/**
 * @author Rhys
 *
 */
public class AndroidServiceWithActionListenerTest extends ServiceTestCase {

  private IBinder binder;

  private String serverURI = TestProperties.serverURI;
  private int waitForCompletionTime = TestProperties.waitForCompletionTime;
  private String activityToken = this.getClass().getCanonicalName();

  //since we know tokens do not work when an action listener isn't specified
  private TestCaseNotifier notifier = new TestCaseNotifier();

  private String classCanonicalName = this.getClass().getCanonicalName();

  /**
   * @param serviceClass
   */
  public AndroidServiceWithActionListenerTest() {
    super(org.eclipse.paho.android.service.MqttService.class);
  }

  @Override
  protected void setUp() throws Exception {

    super.setUp();
    Intent intent = new Intent();
    intent.setClassName("org.eclipse.paho.android.service", "MqttService");
    binder = bindService(intent);

  }

  public void testConnect() throws Throwable {

    IMqttAsyncClient mqttClient = null;
    mqttClient = new MqttClientAndroidService(mContext, serverURI, "testConnect");

    IMqttToken connectToken = null;
    IMqttToken disconnectToken = null;

    connectToken = mqttClient.connect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    connectToken = mqttClient.connect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

  }

  public void testRemoteConnect() throws Throwable {
    String methodName = "testRemoteConnect";
    IMqttAsyncClient mqttClient = null;

    mqttClient = mqttClient = new MqttClientAndroidService(mContext, serverURI, "testRemoteConnect");
    IMqttToken connectToken = null;
    IMqttToken subToken = null;
    IMqttDeliveryToken pubToken = null;
    IMqttToken disconnectToken = null;

    connectToken = mqttClient.connect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    MqttV3Receiver mqttV3Receiver = new MqttV3Receiver(mqttClient,
        null);
    mqttClient.setCallback(mqttV3Receiver);

    MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
    mqttConnectOptions.setCleanSession(false);

    connectToken = mqttClient.connect(mqttConnectOptions, null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    String[] topicNames = new String[]{methodName + "/Topic"};
    int[] topicQos = {0};
    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    byte[] payload = ("Message payload " + classCanonicalName + "." + methodName)
        .getBytes();
    pubToken = mqttClient.publish(topicNames[0], payload, 1, false, null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    boolean ok = mqttV3Receiver.validateReceipt(topicNames[0], 0,
        payload);
    if (!ok) {
      Assert.fail("Receive failed");
    }

    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

  }

  public void testLargeMessage() throws Throwable {
    notifier = new TestCaseNotifier();
    String methodName = "testLargeMessage";
    IMqttAsyncClient mqttClient = null;
    try {
      mqttClient = new MqttClientAndroidService(mContext, serverURI,
          "testLargeMessage");
      IMqttToken connectToken;
      IMqttToken subToken;
      IMqttToken unsubToken;
      IMqttDeliveryToken pubToken;

      MqttV3Receiver mqttV3Receiver = new MqttV3Receiver(mqttClient, null); //TODO do something about this?
      mqttClient.setCallback(mqttV3Receiver);

      connectToken = mqttClient.connect(null, new ActionListener(notifier));
      notifier.waitForCompletion(1000);

      int largeSize = 1000;
      String[] topicNames = new String[]{"testLargeMessage" + "/Topic"};
      int[] topicQos = {0};
      byte[] message = new byte[largeSize];

      java.util.Arrays.fill(message, (byte) 's');

      subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
      notifier.waitForCompletion(1000);

      unsubToken = mqttClient.unsubscribe(topicNames, null, new ActionListener(notifier));
      notifier.waitForCompletion(1000);

      subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
      notifier.waitForCompletion(1000);

      pubToken = mqttClient.publish(topicNames[0], message, 0, false, null, new ActionListener(notifier));
      notifier.waitForCompletion(1000);

      boolean ok = mqttV3Receiver.validateReceipt(topicNames[0], 0,
          message);
      if (!ok) {
        Assert.fail("Receive failed");
      }

    }
    catch (Exception exception) {
      Assert.fail("Failed to instantiate:" + methodName + " exception="
                  + exception);
    }
    finally {
      try {
        IMqttToken disconnectToken;
        disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
        notifier.waitForCompletion(1000);

        mqttClient.close();
      }
      catch (Exception exception) {

      }
    }

  }

  public void testMultipleClients() throws Throwable {
	  
    int publishers = 2;
    int subscribers = 5;
    String methodName = "testMultipleClients";
    IMqttAsyncClient[] mqttPublisher = new IMqttAsyncClient[publishers];
    IMqttAsyncClient[] mqttSubscriber = new IMqttAsyncClient[subscribers];

    IMqttToken connectToken;
    IMqttToken subToken;
    IMqttDeliveryToken pubToken;
    IMqttToken disconnectToken;

    String[] topicNames = new String[]{methodName + "/Topic"};
    int[] topicQos = {0};

    for (int i = 0; i < mqttPublisher.length; i++) {
      mqttPublisher[i] = new MqttClientAndroidService(mContext,
          serverURI, "MultiPub" + i);

      connectToken = mqttPublisher[i].connect(null, new ActionListener(notifier));
      notifier.waitForCompletion(10000);
    } // for...

    MqttV3Receiver[] mqttV3Receiver = new MqttV3Receiver[mqttSubscriber.length];
    for (int i = 0; i < mqttSubscriber.length; i++) {
      mqttSubscriber[i] = new MqttClientAndroidService(mContext,
          serverURI, "MultiSubscriber" + i);
      mqttV3Receiver[i] = new MqttV3Receiver(mqttSubscriber[i],
          null);
      mqttSubscriber[i].setCallback(mqttV3Receiver[i]);

      connectToken = mqttSubscriber[i].connect(null, new ActionListener(notifier));
      notifier.waitForCompletion(10000);

      subToken = mqttSubscriber[i].subscribe(topicNames, topicQos, null, new ActionListener(notifier));
      notifier.waitForCompletion(10000);
    } // for...

    for (int iMessage = 0; iMessage < 2; iMessage++) {
      byte[] payload = ("Message " + iMessage).getBytes();
      for (int i = 0; i < mqttPublisher.length; i++) {
        pubToken = mqttPublisher[i].publish(topicNames[0], payload, 0, false,
            null, new ActionListener(notifier));
        notifier.waitForCompletion(10000);
      }

      TimeUnit.MILLISECONDS.sleep(30000);
      
      for (int i = 0; i < mqttSubscriber.length; i++) {
        for (int ii = 0; ii < mqttPublisher.length; ii++) {
          boolean ok = mqttV3Receiver[i].validateReceipt(
              topicNames[0], 0, payload);
          if (!ok) {
            Assert.fail("Receive failed");
          }
        } // for publishers...
      } // for subscribers...
    } // for messages...

    
    for (int i = 0; i < mqttPublisher.length; i++) {
      disconnectToken = mqttPublisher[i].disconnect(null, null);
      disconnectToken.waitForCompletion();
      mqttPublisher[i].close();
    }
    for (int i = 0; i < mqttSubscriber.length; i++) {
      disconnectToken = mqttSubscriber[i].disconnect(null, null);
      disconnectToken.waitForCompletion();
      mqttSubscriber[i].close();
    }

  }

//  public void testNonDurableSubs() throws Throwable {
//    String methodName = "testNonDurableSubs";
//    notifier = new TestCaseNotifier();
//    IMqttAsyncClient mqttClient = null;
//
//    IMqttToken connectToken;
//    IMqttToken subToken;
//    IMqttToken unsubToken;
//    IMqttDeliveryToken pubToken;
//    IMqttToken disconnectToken;
//
//    mqttClient = new MqttClientAndroidService(mContext, serverURI,
//        "testNonDurableSubs");
//    MqttV3Receiver mqttV3Receiver = new MqttV3Receiver(mqttClient,
//        null);
//    mqttClient.setCallback(mqttV3Receiver);
//    MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
//    // Clean session true is the default and implies non durable
//    // subscriptions.
//    mqttConnectOptions.setCleanSession(true);
//    connectToken = mqttClient.connect(mqttConnectOptions, null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    String[] topicNames = new String[]{methodName + "/Topic"};
//    int[] topicQos = {2};
//    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    byte[] payloadNotRetained = ("Message payload "
//                                 + classCanonicalName + "." + methodName + " not retained")
//        .getBytes();
//    pubToken = mqttClient.publish(topicNames[0], payloadNotRetained, 2, false,
//        null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    boolean ok = mqttV3Receiver.validateReceipt(topicNames[0], 2,
//        payloadNotRetained);
//    if (!ok) {
//      Assert.fail("Receive failed");
//    }
//
//    // Retained publications.
//    // ----------------------
//    byte[] payloadRetained = ("Message payload " + classCanonicalName
//                              + "." + methodName + " retained").getBytes();
//    pubToken = mqttClient.publish(topicNames[0], payloadRetained, 2, true,
//        null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    ok = mqttV3Receiver.validateReceipt(topicNames[0], 2,
//        payloadRetained);
//    if (!ok) {
//      Assert.fail("Receive failed");
//    }
//
//    // Check that unsubscribe and re subscribe resends the publication.
//    unsubToken = mqttClient.unsubscribe(topicNames, null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    ok = mqttV3Receiver.validateReceipt(topicNames[0], 2,
//        payloadRetained);
//    if (!ok) {
//      Assert.fail("Receive failed");
//    }
//
//    // Check that subscribe without unsubscribe receives the
//    // publication.
//    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//    ok = mqttV3Receiver.validateReceipt(topicNames[0], 2,
//        payloadRetained);
//    if (!ok) {
//      Assert.fail("Receive failed");
//    }
//
//    // Disconnect, reconnect and check that the retained publication is
//    // still delivered.
//    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    mqttClient.close();
//
//    mqttClient = new MqttClientAndroidService(mContext, serverURI,
//        "testNonDurableSubs");
//
//    mqttV3Receiver = new MqttV3Receiver(mqttClient,
//        null);
//    mqttClient.setCallback(mqttV3Receiver);
//
//    mqttConnectOptions = new MqttConnectOptions();
//    mqttConnectOptions.setCleanSession(true);
//    connectToken = mqttClient.connect(mqttConnectOptions, null, new ActionListener(notifier));
//    connectToken.waitForCompletion(1000);
//
//    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    ok = mqttV3Receiver.validateReceipt(topicNames[0], 2,
//        payloadRetained);
//    if (!ok) {
//      Assert.fail("Receive failed");
//    }
//
//    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
//    notifier.waitForCompletion(1000);
//
//    mqttClient.close();
//
//  }

  public void testQoSPreserved() throws Throwable {

    IMqttAsyncClient mqttClient = null;
    IMqttToken connectToken;
    IMqttToken subToken;
    IMqttDeliveryToken pubToken;
    IMqttToken disconnectToken;
    String methodName = "testQoSPreserved";

    mqttClient = new MqttClientAndroidService(mContext, serverURI, "testQoSPreserved");
    MqttV3Receiver mqttV3Receiver = new MqttV3Receiver(mqttClient,
        null);
    mqttClient.setCallback(mqttV3Receiver);

    connectToken = mqttClient.connect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    String[] topicNames = new String[]{methodName + "/Topic0",
        methodName + "/Topic1", methodName + "/Topic2"};
    int[] topicQos = {0, 1, 2};
    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

    for (int i = 0; i < topicNames.length; i++) {
      byte[] message = ("Message payload " + classCanonicalName + "."
                        + methodName + " " + topicNames[i]).getBytes();
      for (int iQos = 0; iQos < 3; iQos++) {
        pubToken = mqttClient.publish(topicNames[i], message, iQos, false,
            null, null);
        notifier.waitForCompletion(1000);

        boolean ok = mqttV3Receiver.validateReceipt(topicNames[i],
            Math.min(iQos, topicQos[i]), message);
        if (!ok) {
          Assert.fail("Receive failed sub Qos=" + topicQos[i]
                      + " PublishQos=" + iQos);
        }
      }
    }

    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
    notifier.waitForCompletion(1000);

  }


  public void testHAConnect() throws Throwable{
	
	String methodName = "testHAConnect";

	IMqttAsyncClient client = null;
	try {
	     try {
	    	 	String junk = "tcp://junk:123";
	    	 	client = new MqttClientAndroidService(mContext, junk, methodName);

	    	 	String[] urls = new String[]{"tcp://junk", serverURI};

	    	 	MqttConnectOptions options = new MqttConnectOptions();
	    	 	options.setServerURIs(urls);

	    	 	Log.i(methodName,"HA connect");
	    	 	IMqttToken connectToken = client.connect(options, null, new ActionListener(notifier));
	    	 	notifier.waitForCompletion(waitForCompletionTime);

	    	 	Log.i(methodName,"HA diconnect");
	    	 	IMqttToken disconnectToken = client.disconnect(null, new ActionListener(notifier));
	    	 	notifier.waitForCompletion(waitForCompletionTime);
	    	 	
	    	 	Log.i(methodName,"HA success");
	      }
	      catch (Exception e) {

	    	  	e.printStackTrace();
	    	  	throw e;
	      }
	    }
	    finally {
	      if (client != null) {
	        client.close();
	      }
	    }	  
  }

  public void testPubSub() throws Throwable{
	  
	  String methodName = "testPubSub";
	  IMqttAsyncClient mqttClient = null;
	  try {
	    mqttClient = new MqttClientAndroidService(mContext, serverURI, methodName);
	    IMqttToken connectToken;
	    IMqttToken subToken;
	    IMqttDeliveryToken pubToken;

	    MqttV3Receiver mqttV3Receiver = new MqttV3Receiver(mqttClient, null);
	    mqttClient.setCallback(mqttV3Receiver);

	    connectToken = mqttClient.connect(null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);

	    String[] topicNames = new String[]{"testPubSub" + "/Topic"};
	    int[] topicQos = {0};
	    MqttMessage mqttMessage = new MqttMessage("message for testPubSub".getBytes());
	    byte[] message = mqttMessage.getPayload();

	    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);

	    pubToken = mqttClient.publish(topicNames[0], message, 0, false, null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);

	    TimeUnit.MILLISECONDS.sleep(3000);
	    
	    boolean ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, message);
	    if (!ok) {
	        Assert.fail("Receive failed");
	      }

	    }
	    catch (Exception exception) {
	      Assert.fail("Failed to instantiate:" + methodName + " exception="
	                  + exception);
	    }
	    finally {
	      try {
	        IMqttToken disconnectToken;
	        disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
	        notifier.waitForCompletion(waitForCompletionTime);

	        mqttClient.close();
	      }
	      catch (Exception exception) {

	      }
	    }

  } 
  
  
public void testRetainedMessage() throws Throwable{
	  
	  String methodName = "testRetainedMessage";
	  IMqttAsyncClient mqttClient = null;
	  IMqttAsyncClient mqttClientRetained = null;
	  IMqttToken disconnectToken = null;
	  
	  try {
	    mqttClient = new MqttClientAndroidService(mContext, serverURI, methodName);
	    IMqttToken connectToken;
	    IMqttToken subToken;
	    IMqttDeliveryToken pubToken;

	    MqttV3Receiver mqttV3Receiver = new MqttV3Receiver(mqttClient, null);
	    mqttClient.setCallback(mqttV3Receiver);

	    connectToken = mqttClient.connect(null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);

	    String[] topicNames = new String[]{"testRetainedMessage" + "/Topic"};
	    int[] topicQos = {0};
	    MqttMessage mqttMessage = new MqttMessage("message for testPubSub".getBytes());
	    byte[] message = mqttMessage.getPayload();

	    subToken = mqttClient.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);

	    pubToken = mqttClient.publish(topicNames[0], message, 0, true, null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);

	    TimeUnit.MILLISECONDS.sleep(3000);
	    
	    boolean ok = mqttV3Receiver.validateReceipt(topicNames[0], 0, message);
	    if (!ok) {
	        Assert.fail("Receive failed");
	      }
	    
	    Log.i(methodName, "First client received message successfully");
	    
	    disconnectToken = mqttClient.disconnect(null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);
	    mqttClient.close();
	    
	    mqttClientRetained = new MqttClientAndroidService(mContext, serverURI, "Retained");
	   
	    Log.i(methodName, "New MqttClientAndroidService mqttClientRetained");
	    
	    MqttV3Receiver mqttV3ReceiverRetained = new MqttV3Receiver(mqttClientRetained, null);
	    mqttClientRetained.setCallback(mqttV3ReceiverRetained);
	    
	    Log.i(methodName, "Assigning callback...");

	    connectToken = mqttClientRetained.connect(null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);
	    
	    Log.i(methodName, "Connect to mqtt server");
	    
	    subToken = mqttClientRetained.subscribe(topicNames, topicQos, null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);
	    
	    Log.i(methodName, "subscribe "+topicNames[0].toString() + " QoS is " + topicQos[0]);
	    
	    TimeUnit.MILLISECONDS.sleep(3000);
	    
	    ok = mqttV3ReceiverRetained.validateReceipt(topicNames[0], 0, message);
	    if (!ok) {
	        Assert.fail("Receive retained message failed");
	      }

	    Log.i(methodName, "Second client received message successfully");
	    
	    disconnectToken = mqttClientRetained.disconnect(null, new ActionListener(notifier));
	    notifier.waitForCompletion(waitForCompletionTime);
	    mqttClientRetained.close();
	    
	    }
	    catch (Exception exception) {
	      Assert.fail("Failed to instantiate:" + methodName + " exception="
	                  + exception);
	    }

  }
  private class ActionListener implements IMqttActionListener {

    private TestCaseNotifier notifier = null;

    public ActionListener(TestCaseNotifier notifier) {
      this.notifier = notifier;
    }

    /* (non-Javadoc)
     * @see org.eclipse.paho.client.mqttv3.IMqttActionListener#onFailure(org.eclipse.paho.client.mqttv3.IMqttToken, java.lang.Throwable)
     */
    public void onFailure(IMqttToken token, Throwable exception) {
      notifier.storeException(exception);
      synchronized (notifier) {
        notifier.notifyAll();
      }

    }

    /* (non-Javadoc)
     * @see org.eclipse.paho.client.mqttv3.IMqttActionListener#onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken)
     */
    public void onSuccess(IMqttToken token) {
      synchronized (notifier) {
        notifier.notifyAll();
      }

    }

  }

}