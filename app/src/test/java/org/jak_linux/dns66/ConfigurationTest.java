package org.jak_linux.dns66;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by jak on 07/04/17.
 */
public class ConfigurationTest {

    private Configuration.Item newItemForLocation(String location) {
        Configuration.Item item = new Configuration.Item();
        item.location = location;
        return item;
    }

    @Test
    public void testIsDownloadable() {
        try {
            newItemForLocation(null).isDownloadable();
            fail("Was null");
        } catch (NullPointerException e) {
            // OK
        }

        assertTrue("http:// URI downloadable", newItemForLocation("http://example.com").isDownloadable());
        assertTrue("https:// URI downloadable", newItemForLocation("https://example.com").isDownloadable());
        assertFalse("file:// URI downloadable", newItemForLocation("file://example.com").isDownloadable());
        assertFalse("file:// URI downloadable", newItemForLocation("file:/example.com").isDownloadable());
        assertFalse("https domain not downloadable", newItemForLocation("https.example.com").isDownloadable());
        assertFalse("http domain not downloadable", newItemForLocation("http.example.com").isDownloadable());
    }
}