package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public class AbstractRumaController {

    protected String getFromRumaWithToken(String url, String token) throws IOException {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", String.format("Bearer %s", token));
        conn.setRequestProperty("Content-Type", "application/json");

        return new String(conn.getInputStream().readAllBytes(), Charset.forName("utf8"));
    }

}
