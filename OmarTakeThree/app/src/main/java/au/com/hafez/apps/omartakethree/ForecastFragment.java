package au.com.hafez.apps.omartakethree;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    // we make the adapter global so we can access it from multiple methods.
    ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            new FetchWeatherTask().execute("10028");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Create some dummy data for the ListView.  Here's a sample weekly forecast
        String[] data = {
                "Mon 6/23 - Sunny - 31/17",
                "Tue 6/24 - Foggy - 21/8",
                "Wed 6/25 - Cloudy - 22/17",
                "Thurs 6/26 - Rainy - 18/11",
                "Fri 6/27 - Foggy - 21/10",
                "Sat 6/28 - TRAPPED IN WEATHERSTATION - 23/18",
                "Sun 6/29 - Sunny - 20/7"
        };

        List<String> weekForecast = new ArrayList<String>(Arrays.asList(data));
        // init the adapter.
        mForecastAdapter = new ArrayAdapter<String>(
                getActivity(), // this fragment's parent activity
                R.layout.list_item_forecast, // id of list item layout
                R.id.list_item_forecast_textview, // id of text view to populate
                weekForecast); // data

        // inflating loads the xml and converts it into objects.
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        ListView listView = (ListView)rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        return rootView;
    }

    private class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        /* The date/time conversion code is going to be moved outside the asynctask later,
         * so for convenience we're breaking it out into its own method now.
         */
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }

        private String getWeatherData(String postCode, int numDays) {
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast

                final String BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String POSTCODE_PARAM = "zip";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String APPID_PARAM = "appid";
                final String DAYS_PARAM = "cnt";
                // URL url = new URL(BASE_URL + "zip=10028,us&units=metric&mode=json&appid=7a4d7085838ea084dd111481d105232d&cnt=7");
                Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                        .appendQueryParameter(POSTCODE_PARAM, postCode)
                        .appendQueryParameter(UNITS_PARAM, "metric")
                        .appendQueryParameter(FORMAT_PARAM, "json")
                        .appendQueryParameter(APPID_PARAM, "7a4d7085838ea084dd111481d105232d")
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();

                Log.v(LOG_TAG, builtUri.toString());
                URL url = new URL( builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }

                forecastJsonStr = buffer.toString();
                Log.v(LOG_TAG, forecastJsonStr);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            return forecastJsonStr;
        }

        @Override
        protected String[] doInBackground(String... params) {
            if (params.length == 0)
                return null;

            String postCode = params[0];
            int numDays = 7;
            String forecastDataStr = getWeatherData(postCode, numDays);

            try {
//                JSONObject forecastData = new JSONObject(forecastDataStr);
//                JSONArray days = forecastData.getJSONArray("list");
//                JSONObject day = days.getJSONObject(0);
//                JSONObject temp = day.getJSONObject("temp");
//                JSONArray weather = day.getJSONArray("weather");
//                JSONObject instance = weather.getJSONObject(0);
//                Log.i(LOG_TAG, temp.getDouble("min") + "/" + temp.getDouble("max") + " " + instance.getString("main"));
                return getWeatherDataFromJson(forecastDataStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            super.onPostExecute(strings);

            // need to populate the adapter.
            mForecastAdapter.clear();

            if (strings != null) {
                // load new data
                mForecastAdapter.addAll(strings);
            }
        }
    }
}



//
//private class DownloadFilesTask extends AsyncTask<URL, Integer, Long> {
//    protected Long doInBackground(URL... urls) {
//        int count = urls.length;
//        long totalSize = 0;
//        for (int i = 0; i < count; i++) {
//            totalSize += Downloader.downloadFile(urls[i]);
//            publishProgress((int) ((i / (float) count) * 100));
//            // Escape early if cancel() is called
//            if (isCancelled()) break;
//        }
//        return totalSize;
//    }
//
//    protected void onProgressUpdate(Integer... progress) {
//        setProgressPercent(progress[0]);
//    }
//
//    protected void onPostExecute(Long result) {
//        showDialog("Downloaded " + result + " bytes");
//    }
//}
