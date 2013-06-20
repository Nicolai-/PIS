package dk.bigherman.android.pisviewer;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.System;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import javax.net.ssl.TrustManagerFactorySpi;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.DialogInterface;
import android.database.SQLException;
import android.util.Log;

import android.support.v4.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import dk.bigherman.android.pisviewer.Airfield;
import dk.bigherman.android.pisviewer.DataBaseHelper;

public class MainActivity extends FragmentActivity implements OnCameraChangeListener, OnMarkerClickListener 
{
	GoogleMap gMap;
	String serverIP = "";
	DataBaseHelper myDbHelper;
	long airfieldsColourCodesTimestamp = 0;
	JSONObject airfieldsColourCodes = null;
	//private enum Colour{BLU, WHT, GRN, YLO, AMB, RED, BLK, NIL};

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		myDbHelper = new DataBaseHelper(this.getApplicationContext());
		try 
		{
			// To do, rewrite it ALL
			myDbHelper.createDataBaseIfNotExists();
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

			gMap.setOnCameraChangeListener(this);
			gMap.setOnMarkerClickListener(this);

			loadMarkersTask loader = new loadMarkersTask();
			loader.execute(latLng);
			//            
		}
		myDbHelper = new DataBaseHelper(this.getApplicationContext());
		try 
		{
			// To do, rewrite it ALL
			myDbHelper.createDataBaseIfNotExists();
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

	private JSONObject getAirfieldsColourCodes()
	{
		// Only refresh data every hour
		if (System.currentTimeMillis() > airfieldsColourCodesTimestamp+3600000)
		{
			String jsonString = CommonMethods.getJson("http://" + serverIP + "/test_json.php?getColourCodes");
			try {
				airfieldsColourCodes = new JSONObject(jsonString);
				airfieldsColourCodesTimestamp = System.currentTimeMillis();
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return airfieldsColourCodes;
	}

	private void hideOSDKeyboard(View view)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}

	private void moveCameraToIcao(String icaoCode)
	{
		Log.i("airfields", "Start db load");
		myDbHelper.openDataBase();
		LatLng mapCentre = myDbHelper.icaoToLatLng(icaoCode);
		myDbHelper.close();
		gMap.moveCamera(CameraUpdateFactory.newLatLng(mapCentre));
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
		Log.i("Test", "Hide OSD Keyboard");
		hideOSDKeyboard(view);
		EditText icaoText = (EditText) findViewById(R.id.edit_icao);
		String icaoCode = icaoText.getText().toString().toUpperCase();

		//Validate ICAO code.
		Log.i("Test", "Validate ICAO");
		boolean flag = CommonMethods.validateIcao(icaoCode, myDbHelper);
		// If invalid show error message and return
		if (!flag)
		{
			Toast.makeText(getApplicationContext(), "Invalid ICAO code", Toast.LENGTH_LONG).show();
			return;
		}
		Log.i("Test", "Move Camera");
		moveCameraToIcao(icaoCode);

		updateMetarInfoTask update = new updateMetarInfoTask();
		update.execute(icaoCode);
	} 

	private void drawMapMarkers(List<MarkerOptions> markersOpt)
	{
		for (MarkerOptions markerOpt : markersOpt) 
		{
			gMap.addMarker(markerOpt);
		}
	}

	private List<MarkerOptions> makeListMarkersMetarInformation(ArrayList<Airfield> airfields)
	{
		List<MarkerOptions> markersOpt = new ArrayList<MarkerOptions>();
		String colour = "";

		int icon_state=R.drawable.icn_empty;

		for (Airfield airfield : airfields) {
			// Fuck java, why must I do this twice?
			try {
				colour = getAirfieldsColourCodes().getString(
						airfield.getIcaoCode());
			} catch (JSONException e) {
				// TODO: handle exception
				Log.i("jsonError", e.getMessage());
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

			markersOpt.add(new MarkerOptions().position(new LatLng(airfield.getLat(), airfield.getLng()))
					.title(airfield.getIcaoCode())
					//.snippet(metarJson.getString("report"))
					.icon(BitmapDescriptorFactory.fromResource(icon_state)));
		}
		return markersOpt;		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private class loadMarkersTask extends AsyncTask<LatLng, Void, List<MarkerOptions>> 
	{

		@Override
		protected List<MarkerOptions> doInBackground(LatLng... params) {
			LatLng latLng = params[0];
			LatLngBounds mapBounds = new LatLngBounds(new LatLng(latLng.latitude-3.0, latLng.longitude-(2.5/Math.cos(latLng.latitude*Math.PI/180))), new LatLng(latLng.latitude+3.0, latLng.longitude+(2.5/Math.cos(latLng.latitude*Math.PI/180))));

			myDbHelper.openDataBase();
			ArrayList<Airfield> airfields = myDbHelper.airfieldsInArea(mapBounds);
			myDbHelper.close();
			Log.i("Test", "Make list with markers");

			return makeListMarkersMetarInformation(airfields);
		}

		@Override
		protected void onPostExecute(List<MarkerOptions> result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			Log.i("Test", "Draw markers");
			drawMapMarkers(result);			
		}		
	}

	private class updateMetarInfoTask extends AsyncTask<String, Void, JSONObject>
	{

		@Override
		protected JSONObject doInBackground(String... params) {
			String icaoCode = params[0];
			String readMetarFeed = CommonMethods.getJson("http://" + serverIP + "/test_json.php?icao=" + icaoCode);
			JSONObject metarJson = new JSONObject();

			try {
				metarJson = new JSONObject(readMetarFeed);

				//Log.d(MainActivity.class.getName(), jsonObject.getString("icao"));
				//Log.d(MainActivity.class.getName(), jsonObject.getString("time"));
				//Log.d(MainActivity.class.getName(), jsonObject.getString("report"));
				//Show metar information in whitespace


			} catch (JSONException e) {        	
				e.printStackTrace();
//				Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
			}
			return metarJson;
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			Log.i("Test", "Show Metar Information");
			showMetarText(result);
		}

	}
	
	@Override
	public void onCameraChange(CameraPosition arg0) {
		// TODO Auto-generated method stub'
		Log.i("Test", "Location changed");
		LatLng latLng = arg0.target;

		loadMarkersTask loader = new loadMarkersTask();
		loader.execute(latLng);
	}

	@Override
	public boolean onMarkerClick(Marker arg0) {
		
		updateMetarInfoTask updater = new updateMetarInfoTask();
		String title = arg0.getTitle();
		updater.execute(title);
		// TODO Auto-generated method stub
		return false;
	}
}	
