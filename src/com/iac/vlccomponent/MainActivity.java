
package com.iac.vlccomponent;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;

public class MainActivity extends Activity implements IVideoPlayer {

	String TAG = "Raymond";
	public final static String PLAY_FROM_VIDEOGRID = "org.videolan.vlc.gui.video.PLAY_FROM_VIDEOGRID";

	public final static String PLAY_EXTRA_ITEM_LOCATION = "item_location";
	public final static String PLAY_EXTRA_SUBTITLES_LOCATION = "subtitles_location";
	public final static String PLAY_EXTRA_ITEM_TITLE = "item_title";
	public final static String PLAY_EXTRA_FROM_START = "from_start";

	public final static int SERVER_ADDRESS_NOT_FOUND = 1;
	public final static int SERVER_ADDRESS_FOUND_OK = 2;

	private SurfaceView mSurfaceView;
	private SurfaceView mSubtitlesSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private SurfaceHolder mSubtitlesSurfaceHolder;
	private Surface mSurface = null;
	private Surface mSubtitleSurface = null;
	private LibVLC mLibVLC;

	private final Handler mEventHandler = new VideoPlayerEventHandler(MainActivity.this);
	private final Handler mPlayMediaHandler = new PlayMediaHandler(MainActivity.this);
	private Thread mTryConnAndPlayThread = new GetServerAddrAndPlay(0);
	private Thread mRetryConnAndPlayThread = new GetServerAddrAndPlay(5000);

	//private String mLocation = "rtmp://192.168.2.60:1935/rtmp/live";
	private String mLocation = "http://192.168.11.20:1936/?action=stream";	

	//Volume
	//private AudioManager mAudioManager;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mLibVLC = VLCInstance.get();

		/* Services and miscellaneous */
		//mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

		setContentView(R.layout.activity_main);

		mSurfaceView = (SurfaceView) findViewById(R.id.player_surface);
		mSurfaceHolder = mSurfaceView.getHolder();
		//mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);

		mSubtitlesSurfaceView = (SurfaceView) findViewById(R.id.subtitles_surface);
		mSubtitlesSurfaceHolder = mSubtitlesSurfaceView.getHolder();
		mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
		mSubtitlesSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);

		if (mLibVLC.useCompatSurface())
			mSubtitlesSurfaceView.setVisibility(View.GONE);
		//if (mPresentation == null) {
		mSurfaceHolder.addCallback(mSurfaceCallback);
		mSubtitlesSurfaceHolder.addCallback(mSubtitlesSurfaceCallback);
		//}

		Log.d(TAG, "Hardware acceleration mode: " + Integer.toString(mLibVLC.getHardwareAcceleration()));


		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		//playmedia();
		//mLibVLC.playMRL(mLocation);
		mTryConnAndPlayThread.start();
	}

	/**
	 * attach and disattach surface to the lib
	 */
	private final SurfaceHolder.Callback mSurfaceCallback = new Callback() {
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			if(mLibVLC != null) {
				final Surface newSurface = holder.getSurface();
				if (mSurface != newSurface) {
					mSurface = newSurface;
					Log.d(TAG, "surfaceChanged: " + mSurface);
					mLibVLC.attachSurface(mSurface, MainActivity.this);
				}
			}
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.d(TAG, "surfaceDestroyed");
			if(mLibVLC != null) {
				mSurface = null;
				mLibVLC.detachSurface();
			}
		}
	};

	private final SurfaceHolder.Callback mSubtitlesSurfaceCallback = new Callback() {
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			if(mLibVLC != null) {
				final Surface newSurface = holder.getSurface();
				if (mSubtitleSurface != newSurface) {
					mSubtitleSurface = newSurface;
					mLibVLC.attachSubtitlesSurface(mSubtitleSurface);
				}
			}
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			if(mLibVLC != null) {
				mSubtitleSurface = null;
				mLibVLC.detachSubtitlesSurface();
			}
		}
	};

	private static class ConfigureSurfaceHolder {
		private final Surface surface;
		private boolean configured;

		private ConfigureSurfaceHolder(Surface surface) {
			this.surface = surface;
		}
	}

	@Override
	public int configureSurface(Surface surface, final int width, final int height, final int hal) {
		if (LibVlcUtil.isICSOrLater() || surface == null)
			return -1;
		if (width * height == 0)
			return 0;
		Log.d(TAG, "configureSurface: " + width +"x"+height);

		final ConfigureSurfaceHolder holder = new ConfigureSurfaceHolder(surface);

		final Handler handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (mSurface == holder.surface && mSurfaceHolder != null) {
					if (hal != 0)
						mSurfaceHolder.setFormat(hal);
					mSurfaceHolder.setFixedSize(width, height);
				} else if (mSubtitleSurface == holder.surface && mSubtitlesSurfaceHolder != null) {
					if (hal != 0)
						mSubtitlesSurfaceHolder.setFormat(hal);
					mSubtitlesSurfaceHolder.setFixedSize(width, height);
				}

				synchronized (holder) {
					holder.configured = true;
					holder.notifyAll();
				}
			}
		});

		try {
			synchronized (holder) {
				while (!holder.configured)
					holder.wait();
			}
		} catch (InterruptedException e) {
			return 0;
		}
		return 1;
	}

	@Override
	public void setSurfaceLayout(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
		if (width * height == 0)
			return;
	}

	public void eventHardwareAccelerationError() {
		EventHandler em = EventHandler.getInstance();
		em.callback(EventHandler.HardwareAccelerationError, new Bundle());
	}

	private void startPlay(String playURL) {
		final EventHandler em = EventHandler.getInstance();
		em.addHandler(mEventHandler);
		mLibVLC.playMRL(playURL);
	}

	private void stopPlay() {
		final EventHandler em = EventHandler.getInstance();
		em.removeHandler(mEventHandler);
		//mPlayMediaHandler.removeCallbacks(getServerAddrAndPlay);
		mLibVLC.stop();
	}

	public void stopTryAndRetry() {
		if(mTryConnAndPlayThread != null && !mTryConnAndPlayThread.isInterrupted()) {
			mTryConnAndPlayThread.interrupt();
		}
		if(mRetryConnAndPlayThread != null && !mRetryConnAndPlayThread.isInterrupted()) {
			mRetryConnAndPlayThread.interrupt();
		}
	}

	public void RetryHttpConnThread() {
		mRetryConnAndPlayThread.interrupt();
		mRetryConnAndPlayThread = new GetServerAddrAndPlay(5000);
		mRetryConnAndPlayThread.start();
	}

	public class GetServerAddrAndPlay extends Thread {

		long mSleepTime;

		public GetServerAddrAndPlay(long sleepTime) {
			mSleepTime = sleepTime;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(mSleepTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Message msg = new Message();
			Bundle data = new Bundle();
			String playAddress = mLocation;//requestHttpCon();
			if(!Thread.currentThread().isInterrupted()) {
				if(TextUtils.isEmpty(playAddress)) {
					data.putInt("event",SERVER_ADDRESS_NOT_FOUND);
				}
				else {
					data.putString("result",playAddress);
					data.putInt("event",SERVER_ADDRESS_FOUND_OK);
				}
				Log.i(TAG, "event == " + data.getInt("event"));
				msg.setData(data);
				mPlayMediaHandler.sendMessage(msg);
			}
		}
	}

	public String requestHttpCon() {
		String ipAddress = wifiIpAddress();
		if(TextUtils.isEmpty(ipAddress))
			return null;
		int lastDotIndex = ipAddress.lastIndexOf(".");
		String subAddress = ipAddress.substring(0, lastDotIndex+1);
		String request_url = null;
		for(int ipIndex = 1; ipIndex <= 255; ipIndex++) {
			try {
				request_url = "http://"+ subAddress + String.valueOf(ipIndex) + ":1936/?action=stream";
				URL url = new URL(request_url);		    

				HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
				//urlc.setRequestProperty("User-Agent", "Android Application:"+"4.4.3");
				urlc.setRequestProperty("Connection", "close");
				urlc.setConnectTimeout(100); // ms
				urlc.connect();

				if (urlc.getResponseCode() == 200) {
					Log.i(TAG, "getResponseCode == 200 OK ; request_url = " + request_url);
					return request_url;
				}
			} catch (MalformedURLException e1) {
				//e1.printStackTrace();
				Log.e(TAG, "MalformedURLException, return null");
				return null;
			} catch (IOException e) {
				Log.e(TAG, "IOException : request_url = " + request_url);
				//e.printStackTrace();
				//return null;
			}
		}
		Log.e(TAG, "request_url = null");
		return null;
	}

	protected String wifiIpAddress() {
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

		// Convert little-endian to big-endianif needed
		if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
			ipAddress = Integer.reverseBytes(ipAddress);
		}

		byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

		String ipAddressString;
		try {
			ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
		} catch (UnknownHostException ex) {
			Log.e("WIFIIP", "Unable to get host address.");
			ipAddressString = null;
		}

		return ipAddressString;
	}

	private static class PlayMediaHandler extends WeakHandler<MainActivity> {

		private String TAG = "Raymond";
		public PlayMediaHandler(MainActivity owner) {
			super(owner);
		}

		@Override
		public void handleMessage(Message msg) {
			MainActivity activity = getOwner();
			Log.i(TAG, "PlayMediaHandler handle msg, event = " + msg.getData().getInt("event"));
			if(activity == null) {
				Log.i(TAG,"activity = null");
				return;
			}
			// Do not handle events if we are leaving the VideoPlayerActivity

			Bundle data = msg.getData();
			if(data == null) {
				Log.i(TAG,"data = null");
				return;
			}

			switch (msg.getData().getInt("event")) {
			//Log.i(TAG, "PlayMediaHandler handle msg");
			case SERVER_ADDRESS_NOT_FOUND :
				Log.i(TAG, "SERVER_ADDRESS_NOT_FOUND");
				// server ip addr can't found, waiting for 5 sec and try again 
				Log.i(TAG,"server ip not found, try waiting for 5 sec and try again");
				activity.RetryHttpConnThread();
				//activity.mRetryHttpConnThread.start();
				break;
			case SERVER_ADDRESS_FOUND_OK :
				Log.i(TAG, "SERVER_ADDRESS_FOUND_OK");
				String val = data.getString("result");
				Log.i(TAG,"server ip addr is:" + val);
				activity.startPlay(val);
				break;
			}
		}
	};

	private static class VideoPlayerEventHandler extends WeakHandler<MainActivity> {

		private String TAG = "Raymond";
		public VideoPlayerEventHandler(MainActivity owner) {
			super(owner);
		}

		@Override
		public void handleMessage(Message msg) {
			MainActivity activity = getOwner();
			if(activity == null) return;
			// Do not handle events if we are leaving the VideoPlayerActivity

			switch (msg.getData().getInt("event")) {
			case EventHandler.MediaParsedChanged:
				Log.i(TAG, "MediaParsedChanged");
				break;
			case EventHandler.MediaPlayerPlaying:
				Log.i(TAG, "MediaPlayerPlaying");
				break;
			case EventHandler.MediaPlayerPaused:
				Log.i(TAG, "MediaPlayerPaused");
				break;
			case EventHandler.MediaPlayerStopped:
				activity.stopTryAndRetry();
				Log.i(TAG, "MediaPlayerStopped");
				activity.stopPlay();
				// waiting for 5 sec and retry search server addr and play
				activity.RetryHttpConnThread();
				break;
			case EventHandler.MediaPlayerEndReached:
				Log.i(TAG, "MediaPlayerEndReached");
				break;
			case EventHandler.MediaPlayerVout:
				Log.i(TAG, "MediaPlayerVout");
				break;
			case EventHandler.MediaPlayerPositionChanged:
				//Log.i(TAG, "MediaPlayerPositionChanged");
				break;
			case EventHandler.MediaPlayerEncounteredError:
				Log.i(TAG, "MediaPlayerEncounteredError");
				break;
			case EventHandler.HardwareAccelerationError:
				Log.i(TAG, "HardwareAccelerationError");
				break;
			case EventHandler.MediaPlayerTimeChanged:
				//Log.i(TAG, "MediaPlayerTimeChanged");
				break;
			case EventHandler.MediaPlayerESAdded:
				Log.i(TAG, "MediaPlayerESAdded");
				break;
			case EventHandler.MediaPlayerESDeleted:
				Log.i(TAG, "MediaPlayerESDeleted");
				break;
			default:
				break;
			}
		}
	};
}
