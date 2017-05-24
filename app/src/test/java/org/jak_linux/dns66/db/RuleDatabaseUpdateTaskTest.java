package org.jak_linux.dns66.db;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Created by jak on 19/05/17.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class, Uri.class})
public class RuleDatabaseUpdateTaskTest {

    HashMap<String, Uri> uriLocations = new HashMap<>();

    private Configuration.Item newItemForLocation(String location) {
        Configuration.Item item = new Configuration.Item();
        item.location = location;
        return item;
    }

    @Before
    public void setUp() throws Exception {
        mockStatic(Log.class);
        mockStatic(Uri.class);

        when(Uri.class, "parse", anyString()).thenAnswer(new Answer<Uri>() {

            @Override
            public Uri answer(InvocationOnMock invocation) throws Throwable {
                return newUri(invocation.getArgumentAt(0, String.class));
            }
        });

    }

    @Test
    public void testReleaseGarbagePermissions() throws Exception {
        Context mockContext = mock(Context.class);
        ContentResolver mockResolver = mock(ContentResolver.class);
        when(mockContext.getContentResolver()).thenReturn(mockResolver);

        final List<UriPermission> persistedPermissions = new LinkedList<>();
        when(mockResolver.getPersistedUriPermissions()).thenReturn(persistedPermissions);

        UriPermission usedPermission = mock(UriPermission.class);
        when(usedPermission.getUri()).thenReturn(newUri("content://used"));
        persistedPermissions.add(usedPermission);

        UriPermission garbagePermission = mock(UriPermission.class);
        when(garbagePermission.getUri()).thenReturn(newUri("content://garbage"));
        persistedPermissions.add(garbagePermission);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Iterator<UriPermission> iter = persistedPermissions.iterator();
                while (iter.hasNext()) {
                    UriPermission perm = iter.next();
                    if (perm.getUri() == invocation.getArgumentAt(0, Uri.class))
                        iter.remove();
                }
                return null;
            }
        }).when(mockResolver, "releasePersistableUriPermission", any(Uri.class), anyInt());

        Configuration configuration = new Configuration();
        configuration.hosts.items.add(newItemForLocation("content://used"));

        assertTrue(persistedPermissions.contains(usedPermission));
        assertTrue(persistedPermissions.contains(garbagePermission));

        new RuleDatabaseUpdateTask(mockContext, configuration, false).releaseGarbagePermissions();

        assertTrue(persistedPermissions.contains(usedPermission));
        assertFalse(persistedPermissions.contains(garbagePermission));
    }

    private Uri newUri(String location) throws Exception {
        if (uriLocations.containsKey(location))
            return uriLocations.get(location);

        Uri uri = PowerMockito.mock(Uri.class);
        uriLocations.put(location, uri);

        return uri;
    }
}
