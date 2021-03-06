package course.labs.dailyselfie;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Pattern;

import android.app.AlarmManager;
import android.app.ListActivity;
import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.os.Build;
import android.provider.MediaStore;

public class DailySelfieMainActivity extends ListActivity {
	public static String SHOW_IMAGE = "show_photo_path";
	
	private final int TAKE_PHOTO_ACTION_CODE = 1;
	private String JPEG_FILE_SUFFIX = ".jpg";
	private String TAG = "Lab-DailySelfie";
	private static final long INTERVAL_TWO_MINUTES = 2 * 60 * 1000L;
	
	// context menu
	private static final int CONTEXT_MENU_VIEW = 1;
	private static final int CONTEXT_MENU_DELETE = 2;
	private static final int CONTEXT_MENU_DELETEALL = 3;
	
	private String mAlbumName = "DailySelfie";
	private File mAlbumDir;
	
	private String mCurrentPhotoPath;
	
	private PhotoViewAdapter mAdapter;
	private AlarmManager mAlarmManager;
	private Intent mNotificationReceiverIntent;
	private PendingIntent mNotificationReceiverPendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "enter onCreate()");
        
        setContentView(R.layout.photo_view);
        
		mAdapter = new PhotoViewAdapter(getApplicationContext());
		setListAdapter(mAdapter);
		
		ListView lv = getListView();
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.i(TAG, "Click listView(position="+position+", id="+id);
				
				PhotoRecord image = mAdapter.getItem((int)id);
				// display photo with ImageActivity
				displayFullSizePhoto(image.getPhotoPath());
				
				// display photo with system view
				// displayFullSizePhotoWithSysView(image.getPhotoPath());
			}
		});
		
		// register context menu
		this.registerForContextMenu(lv);
		
		mAlbumDir = null;
		
        try {
        	// get the album dir. If not exists, create it
        	mAlbumDir = getmAlbumPath();
        } catch (Exception e) {
        	Log.i(TAG, "Could not make or get external storage path for photo.");
        }

        batchDisplayPhotoInAlbumDir();
        
        createAlaram();
        
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
	        case R.id.menu_action_camera:
	        	dispatchTakePictureIntent(TAKE_PHOTO_ACTION_CODE);
	        	
	            return true;
	            
	        case R.id.menu_cancel_alarm:
	        	cancel_alarm();
	        	
	        	return true;
	        	
	        default:
	        	return super.onOptionsItemSelected(item);
        }
        
        
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        Log.v(TAG, "onCreateContextMenu()");
        
        // set context menu title
        menu.setHeaderTitle("operation");
        
        // add context menu item
        menu.add(0, CONTEXT_MENU_VIEW, Menu.NONE, "View");
        menu.add(0, CONTEXT_MENU_DELETE, Menu.NONE, "Delete");
        menu.add(0, CONTEXT_MENU_DELETEALL, Menu.NONE, "Delete All");

    }
    
    

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
    	int position = menuInfo.position;

        Log.i(TAG, "context item seleted ID="+ menuInfo.id);
        
        switch(item.getItemId()) {
        case CONTEXT_MENU_VIEW:
        	PhotoRecord image = mAdapter.getItem(position);
			
			// display photo with ImageActivity
			// displayFullSizePhoto(image.getPhotoPath());
			
			// display photo with system view
			displayFullSizePhotoWithSysView(image.getPhotoPath());
			
			return true;
			
        case CONTEXT_MENU_DELETE:
        	String photoPath = mAdapter.getItem(position).getPhotoPath();
        	
        	// delete file and item in the listView
        	deleteImageFile(photoPath);
        	mAdapter.deleteItem(position);
        	
        	return true;
        	
        	// break;
        case CONTEXT_MENU_DELETEALL:
        	deleteAllImage();
        	return true;
        	
        default:
            return super.onContextItemSelected(item);
        }
        
        // return true;
    }
  
    private void deleteImageFile(String filePath) {
    	File f = new File(filePath);
    	boolean success = false;
    	
    	if (f.exists()) {
    		success = f.delete();
    		Log.i(TAG, "delete file : "+filePath);
    		
    		if (success) {
        		galleryRefeshPic(filePath);
    		}
    	}  
    	
    }
    
    
    private void deleteAllImage() {
    	
    	String[] filelist = mAlbumDir.list(filenamefilter);
    	
    	for (String filename : filelist) {
    		String filePath = mAlbumDir + File.separator + filename;
    		
    		deleteImageFile(filePath);
        		
        	Log.i(TAG, "delete file "+filePath);
        	  
        }
    	    	
    	mAdapter.removeAllViews();
    }

    //  invokes an intent to capture a photo. 
    private void dispatchTakePictureIntent(int actionCode) {
        
        File imageResultFile = null;
        
        try {
        	imageResultFile = createImageFile();
        } catch (Exception  e) {
        	Log.i(TAG, "Fail to create image file:"+e.getLocalizedMessage());
        	return;
        }

    	Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);    
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageResultFile));

        // startActivity(takePictureIntent);
        startActivityForResult(takePictureIntent, TAKE_PHOTO_ACTION_CODE);
 
    }
    
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	 switch (requestCode) {
    	 case TAKE_PHOTO_ACTION_CODE:
    		 if (resultCode == RESULT_OK)  {
    			Bitmap imageBitmap;
    			 
    			if (data != null) {  // not set: intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
    				
    				Bundle extras = data.getExtras();
    	    	    imageBitmap = (Bitmap) extras.get("data"); 
    	    	    Log.i(TAG, "in onActivityResult(TAKE_PHOTO_ACTION_CODE): get returned imageBitmap.");
    	    	    
    			}  else {    		        
    			     	PhotoRecord newPhoto = new PhotoRecord(mCurrentPhotoPath);
    			     	mAdapter.add(newPhoto);
    		        } 
    				galleryRefeshPic(mCurrentPhotoPath);
    			}   else {   // cancel or fail
    				File f = new File(mCurrentPhotoPath);
    				if (f.exists()) {
    					f.delete();
    				}
    			}
    	  
    	 }
   	}
    
    private File createImageFile() throws Exception {
    	 
    	if (mAlbumDir == null) {
    		mAlbumDir = getmAlbumPath();
    	}
    	
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        String filePath = mAlbumDir + File.separator + timeStamp + JPEG_FILE_SUFFIX;
        Log.i(TAG, "createImageFile:"+filePath);
        
        File image = new File(filePath);
        if (image.exists()) {
        	image.delete();
        }
        
        image.createNewFile();
        
        mCurrentPhotoPath = image.getAbsolutePath();

        return image;
    }
    
    private File getmAlbumPath() throws Exception {
    	String status = Environment.getExternalStorageState();
    	
    	if (!status.equals(Environment.MEDIA_MOUNTED)) {
    		String errMsg = "External Storage wasn't mounted for writing to create Album dir:"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+mAlbumName;
  			Toast.makeText(getApplicationContext(), errMsg, Toast.LENGTH_LONG);
  			throw new Exception(errMsg);
    	}
    	
    	// create AlbumDir
    	File AlbumDir = new File(
	    					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), 
	    					mAlbumName
    					);
    	
    	if(!AlbumDir.exists()) {
   		 if (AlbumDir.mkdirs() == false) {
   			 String errMsg = "Can't create Album dir:"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+mAlbumName;
   			 Toast.makeText(getApplicationContext(), errMsg, Toast.LENGTH_LONG);
   			 throw new IOException(errMsg);
   		 }
   		 
   	 	}
    	
    	return AlbumDir;
    }
    
    // invoke the system's media scanner to add your photo to the Media Provider's database, 
    // making it available in the Android Gallery application and to other apps.
    private void galleryRefeshPic(String filename) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(filename);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
    
    private void batchDisplayPhotoInAlbumDir() {
    	String[] filelist = mAlbumDir.list(filenamefilter);
    	Arrays.sort(filelist);
    	
    	for (int idx = filelist.length-1; idx>=0; idx--) {
    		String filePath = mAlbumDir + File.separator + filelist[idx];
    		Log.i(TAG, "display:"+filePath);
    		
            PhotoRecord newPhoto = new PhotoRecord(filePath);
	     	mAdapter.addTail(newPhoto);
    	}
        	
    }
    
  //create a FilenameFilter and override its accept-method

    FilenameFilter filenamefilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String filename) {
			// TODO Auto-generated method stub
			 if (filename.endsWith(JPEG_FILE_SUFFIX)) {
	                return true;
	            }
	          return false;
		}
    };
    
    private void displayFullSizePhoto(String path) {
    	
    	// Create an intent stating which Activity you would like to
		// start
    	Intent showImageIntent = new Intent();  
    	showImageIntent.setClass(DailySelfieMainActivity.this, ImageActivity.class);

    	// store the path of photo to show  	
    	showImageIntent.putExtra(SHOW_IMAGE, path);

    	// Launch the Activity using the intent
		startActivity(showImageIntent);
    }
   
    // show picutres with ACTION_VIEW
    private void displayFullSizePhotoWithSysView(String path) {
    	
    	// Create an intent stating which Activity you would like to
		// start
    	Intent showImageIntent = new Intent(Intent.ACTION_VIEW);  
    	if (showImageIntent.resolveActivity(getPackageManager()) == null) {
    		Toast.makeText(getApplicationContext(), 
    						"No application could be used to view the pictures", 
    						Toast.LENGTH_LONG)
    			.show();	
    	}  else {
    		Uri uri = Uri.fromFile(new File(path));
        	showImageIntent.setDataAndType(uri, "image/");
        	showImageIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            
        	// Launch the Activity using the intent
    		startActivity(showImageIntent);
    	}
    }
    
    private void createAlaram() {
    	Log.i(TAG, "Set Repeating alarm at:"+ DateFormat.getDateTimeInstance().format(new Date()));
    	// Get the AlarmManager Service
    	mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

    	// Create an Intent to broadcast to the AlarmNotificationReceiver
    	mNotificationReceiverIntent = new Intent(DailySelfieMainActivity.this,
    					AlarmNotificationReceiver.class);

    	// Create an PendingIntent that holds the NotificationReceiverIntent
    	mNotificationReceiverPendingIntent = PendingIntent.getBroadcast(
    					DailySelfieMainActivity.this, 0, mNotificationReceiverIntent, 0);
    	
    	// Set repeating alarm
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + INTERVAL_TWO_MINUTES,
				INTERVAL_TWO_MINUTES,
				mNotificationReceiverPendingIntent);
    }
    
    private void cancel_alarm() {
    	// cancel all alarm using mNotificationReceiverPendingIntent
    	mAlarmManager.cancel(mNotificationReceiverPendingIntent);
    	
    	Toast.makeText(getApplicationContext(), "Stop Alarm about DailySelfie!", Toast.LENGTH_LONG)
    		.show();
    	
    }
}
