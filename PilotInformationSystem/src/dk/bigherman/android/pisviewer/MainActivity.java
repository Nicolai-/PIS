package dk.bigherman.android.pisviewer;

import android.os.Bundle;
import android.os.StrictMode;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;

import android.support.v4.app.FragmentActivity;
 
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import dk.bigherman.android.pisviewer.Airfield;
import dk.bigherman.android.pisviewer.DataBaseHelper;

public class MainActivity extends FragmentActivity 
{
	GoogleMap gMap;
	String serverIP = "";
	DataBaseHelper myDbHelper;
	//private enum Colour{BLU, WHT, GRN, YLO, AMB, RED, BLK, NIL};
	

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		
		if (android.os.Build.VERSION.SDK_INT > 9) {
			StrictMode.ThreadPolicy policy = 
			        new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
			}
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
        // Getting Google Play availability status
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());
 
        // Showing status
        if(status!=ConnectionResult.SUCCESS)
        { 	// Google Play Services are not available
 
            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();
 
        }
        else
        {
        	// Google Play Services are available
 
            // Getting reference to the SupportMapFragment of activity_main.xml
            SupportMapFragment fm = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            gMap = fm.getMap();
        	// Creating a LatLng object for the current location (somewhere near Aarhus! :-))
            LatLng latLng = new LatLng(56.0, 10.3);
     
            // Showing the current location in Google Map
            gMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
     
            // Zoom in the Google Map at a level where all (most) of Denmark will be visible
            gMap.animateCamera(CameraUpdateFactory.zoomTo(6));
        }
		myDbHelper = new DataBaseHelper(this.getApplicationContext());
		try 
		{
			// To do, rewrite it ALL
			myDbHelper.createDataBase();
		}
		catch (IOException ioe)
		{
			throw new Error("Unable to create database");
		}
		catch(SQLException sqle)
		{
			throw sqle;
		}
		
		serverIP = getResources().getString(R.string.server_ip);
	}
	
	public boolean onOptionsItemSelected (MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menu_settings:
				final AlertDialog.Builder alert = new AlertDialog.Builder(this);
				
				alert.setTitle("Set Server IP Address");
				alert.setIcon(R.drawable.setserver_inverse);
				
			    final EditText input = new EditText(this);
			    alert.setView(input);
			    alert.setPositiveButton("OK", new DialogInterface.OnClickListener() 
			    {
			        public void onClick(DialogInterface dialog, int whichButton) 
			        {
			            String value = input.getText().toString().trim();
			            //Toast.makeText(getApplicationContext(), value, Toast.LENGTH_SHORT).show();
			            serverIP = value;
			        }
			    });

			    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
			    {
			        public void onClick(DialogInterface dialog, int whichButton)
			        {
			            dialog.cancel();
			        }
			    });
			    alert.show();
				break;
		}
		return true;
	}
	
	private void hideOSDKeyboard(View view)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}
	
	private LatLngBounds moveCameraToIcao(String icaoCode)
	{
		Log.i("airfields", "Start db load");
		myDbHelper.openDataBase();
        LatLng mapCentre = myDbHelper.icaoToLatLng(icaoCode);
	 	myDbHelper.close();
        gMap.moveCamera(CameraUpdateFactory.newLatLng(mapCentre));
        LatLngBounds mapBounds = new LatLngBounds(new LatLng(mapCentre.latitude-3.0, mapCentre.longitude-(2.5/Math.cos(mapCentre.latitude*Math.PI/180))), new LatLng(mapCentre.latitude+3.0, mapCentre.longitude+(2.5/Math.cos(mapCentre.latitude*Math.PI/180))));
        gMap.animateCamera(CameraUpdateFactory.newLatLngBounds(mapBounds, 0));
        return mapBounds;
	}
	
	private void showMetarText(JSONObject metarJson)
	{
    	String metar = "";
		try {
			metar = metarJson.getString("report");
		} catch (JSONException e) {
			e.printStackTrace();
		}    
		TextView textMetar = (TextView) findViewById(R.id.text_metar);
		textMetar.setText(metar);
	}
	
	public void showMetar(View view)
	{
    	List<MarkerOptions> markersOpt;
    	Log.i("Test", "Hide OSD Keyboard");
		hideOSDKeyboard(view);
		EditText icaoText = (EditText) findViewById(R.id.edit_icao);
    	String icaoCode = icaoText.getText().toString().toUpperCase();
    	
    	//Validate ICAO code.
    	Log.i("Test", "Validate ICAO");
    	boolean flag = CommonMethods.validateIcao(icaoCode, "^[A-Z]{4}$", myDbHelper);
    	// If invalid show error message and return
    	if (!flag)
    	{
	    	Toast.makeText(getApplicationContext(), "Invalid ICAO code", Toast.LENGTH_LONG).show();
    		return;
    	}
    	Log.i("Test", "Move Camera");
		LatLngBounds mapBounds = moveCameraToIcao(icaoCode);

		myDbHelper.openDataBase();
	 	ArrayList<Airfield> airfields = myDbHelper.airfieldsInArea(mapBounds);
	 	myDbHelper.close();
    	
	 	//Replace with some background thread
    	String readMetarFeed = readMetarFeed(icaoCode);
    	
    	try {
	    	JSONObject jsonObject = new JSONObject(readMetarFeed);
	    	
	    	//Log.d(MainActivity.class.getName(), jsonObject.getString("icao"));
	    	//Log.d(MainActivity.class.getName(), jsonObject.getString("time"));
	    	//Log.d(MainActivity.class.getName(), jsonObject.getString("report"));
	    	//Show metar information in whitespace
	    	Log.i("Test", "Show Metar Information");
	    	showMetarText(jsonObject);
	    	Log.i("Test", "Make list with markers");
	    	markersOpt = makeListMarkersMetarInformation(airfields, mapBounds);
	    	Log.i("Test", "Draw markers");
	    	drawMapMarkers(markersOpt);
	    	
        } catch (JSONException e) {        	
        	e.printStackTrace();
    		Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    } 
	
	private void drawMapMarkers(List<MarkerOptions> markersOpt)
	{
		for (MarkerOptions markerOpt : markersOpt) 
		{
			gMap.addMarker(markerOpt);
		}
	}

	private List<MarkerOptions> makeListMarkersMetarInformation(ArrayList<Airfield> airfields, LatLngBounds mapBounds)
	{
		List<Marker> markers = new ArrayList<Marker>();

		List<MarkerOptions> markersOpt = new ArrayList<MarkerOptions>();
		String colour = "";
		JSONObject metarJson = new JSONObject();
		
		Log.i("airfields", "Next airfield call, NE=" + mapBounds.northeast.toString());
	 	int icon_state=R.drawable.icn_empty;
                  
        for (int i=0; i<airfields.size();i++)
        {
        	String readMetarFeed = readMetarFeed(airfields.get(i).getIcaoCode());
        	Log.i("airfields", airfields.get(i).getIcaoCode());
        	
        	if(readMetarFeed != "")
        	{
        		Log.i("airfields", readMetarFeed);
        		
    	    	try {
            		metarJson = new JSONObject(readMetarFeed);
					colour = metarJson.getString("colour");
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    	    	if (colour.contentEquals("BLU"))
    	    	{
    	    		icon_state=R.drawable.icn_blue;
    	    	}
    	    	else if (colour.contentEquals("WHT"))
    	    	{
    	    		icon_state=R.drawable.icn_white;
    	    	}
    	    	else if (colour.contentEquals("GRN"))
    	    	{
    	    		icon_state=R.drawable.icn_green;
    	    	}
    	    	else if (colour.contentEquals("YLO"))
    	    	{
    	    		icon_state=R.drawable.icn_yellow;
    	    	}
    	    	else if (colour.contentEquals("AMB"))
    	    	{
    	    		icon_state=R.drawable.icn_amber;
    	    	}
    	    	else if (colour.contentEquals("RED"))
    	    	{
    	    		icon_state=R.drawable.icn_red;
    	    	}
    	    	else if (colour.contentEquals("NIL"))
    	    	{
    	    		icon_state=R.drawable.icn_empty;
    	    	}
        	}
        		try {
					markersOpt.add(new MarkerOptions().position(new LatLng(airfields.get(i).getLat(), airfields.get(i).getLng()))
					.title(airfields.get(i).getName())
					.snippet(metarJson.getString("report"))
					.icon(BitmapDescriptorFactory.fromResource(icon_state)));
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        }
        return markersOpt;		
	}
	
	private String readMetarFeed(String icaoCode) 
	{
		int tries = 1;
		int maxTries = 5;
		//String metarURL = "http://duku.no-ip.info/pis/android/jason.php?i=" + icaoCode;

		String metarURL = "http://" + serverIP + "/test_json.php?icao="
				+ icaoCode;
		StringBuilder builder = new StringBuilder();
		HttpGet httpGet = new HttpGet(metarURL);
		HttpParams httpParameters = new BasicHttpParams();
		// Set the timeout in milliseconds until a connection is established.
		// The default value is zero, that means the timeout is not used. 
		int timeoutConnection = 5000;
		HttpConnectionParams.setConnectionTimeout(httpParameters,
				timeoutConnection);
		// Set the default socket timeout (SO_TIMEOUT) 
		// in milliseconds which is the timeout for waiting for data.
		int timeoutSocket = 5000;
		HttpConnectionParams
				.setSoTimeout(httpParameters, timeoutSocket);
		DefaultHttpClient client = new DefaultHttpClient(httpParameters);
		client.setParams(httpParameters);
		do {
			try {
				Log.i("Test", "This is try nr: " + tries);
				HttpResponse response = client.execute(httpGet);
				StatusLine statusLine = response.getStatusLine();
				int statusCode = statusLine.getStatusCode();
				if (statusCode == 200) {
					HttpEntity entity = response.getEntity();
					InputStream content = entity.getContent();
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(content));
					String line;
					while ((line = reader.readLine()) != null) {
						builder.append(line);
					}
				} else {
					Log.e(MainActivity.class.toString(),
							"Failed to download file");
				}
				break;
			} catch (ClientProtocolException e) {
				e.printStackTrace();
				throw new RuntimeException(
						"Error connecting to server - check IP address.  Use Settings menu to fix this");
			} catch (ConnectTimeoutException e) {
				e.printStackTrace();
				tries--;
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(
						"Error connecting to server - check IP address.  Use Settings menu to fix this");
			}
		} while (tries <= 5);
		return builder.toString();
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
}
