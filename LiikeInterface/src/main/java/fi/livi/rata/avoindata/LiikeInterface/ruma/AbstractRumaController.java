package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AbstractRumaController {

    protected String getFromRumaWithToken(String url, String token) throws IOException {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        InputStream is = null;
        try {
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", String.format("Bearer %s", token));
            conn.setRequestProperty("Content-Type", "application/json");
            is = conn.getInputStream();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            if (is != null) {
                is.close();
            }
            conn.disconnect();
        }
    }

}
