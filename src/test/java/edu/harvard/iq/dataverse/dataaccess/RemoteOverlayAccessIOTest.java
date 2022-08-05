/*
 * Copyright 2018 Forschungszentrum Jülich GmbH
 * SPDX-License-Identifier: Apache 2.0
 */
package edu.harvard.iq.dataverse.dataaccess;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.GlobalId;
import edu.harvard.iq.dataverse.mocks.MocksFactory;
import edu.harvard.iq.dataverse.util.UrlSignerUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.io.IOException;
import java.nio.file.Paths;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
public class RemoteOverlayAccessIOTest {

    @Mock

    private Dataset dataset;
    private DataFile datafile;
    private String logoPath = "resources/images/dataverse_project_logo.svg";
    private String pid = "10.5072/F2/ABCDEF";

    @BeforeEach
    public void setUp() {
        System.setProperty("dataverse.files.test.type", "remote");
        System.setProperty("dataverse.files.test.label", "testOverlay");
        System.setProperty("dataverse.files.test.base-url", "https://demo.dataverse.org");
        System.setProperty("dataverse.files.test.base-store", "file");
        System.setProperty("dataverse.files.test.download-redirect", "true");
        System.setProperty("dataverse.files.test.remote-store-name", "DemoDataCorp");
        System.setProperty("dataverse.files.test.secret-key", "12345"); // Real keys should be much longer, more random
        System.setProperty("dataverse.files.file.type", "file");
        System.setProperty("dataverse.files.file.label", "default");
        datafile = MocksFactory.makeDataFile();
        dataset = MocksFactory.makeDataset();
        dataset.setGlobalId(GlobalId.parse("doi:" + pid).get());
        datafile.setOwner(dataset);
        datafile.setStorageIdentifier("test://" + logoPath);

    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("dataverse.files.test.type");
        System.clearProperty("dataverse.files.test.label");
        System.clearProperty("dataverse.files.test.base-url");
        System.clearProperty("dataverse.files.test.base-store");
        System.clearProperty("dataverse.files.test.download-redirect");
        System.clearProperty("dataverse.files.test.label");
        System.clearProperty("dataverse.files.test.remote-store-name");
        System.clearProperty("dataverse.files.test.secret-key");
        System.clearProperty("dataverse.files.file.type");
        System.clearProperty("dataverse.files.file.label");
    }

    @Test
    void testRemoteOverlayFile() throws IOException {
        // We can read the storageIdentifier and get the driver
        assertTrue(datafile.getStorageIdentifier()
                .startsWith(DataAccess.getStorgageDriverFromIdentifier(datafile.getStorageIdentifier())));
        // We can get the driver type from it's ID
        assertTrue(DataAccess.getDriverType("test").equals(System.getProperty("dataverse.files.test.type")));
        // When we get a StorageIO for the file, it is the right type
        StorageIO<DataFile> storageIO = DataAccess.getStorageIO(datafile);
        assertTrue(storageIO instanceof RemoteOverlayAccessIO);
        // When we use it, we can get properties like the remote store name
        RemoteOverlayAccessIO<DataFile> remoteIO = (RemoteOverlayAccessIO<DataFile>) storageIO;
        assertTrue(remoteIO.getRemoteStoreName().equals(System.getProperty("dataverse.files.test.remote-store-name")));
        // And can get a temporary download URL for the main file
        String signedURL = remoteIO.generateTemporaryDownloadUrl(null, null, null);
        // And the URL starts with the right stuff
        assertTrue(signedURL.startsWith(System.getProperty("dataverse.files.test.base-url") + "/" + logoPath));
        // And the signature is valid
        assertTrue(
                UrlSignerUtil.isValidUrl(signedURL, null, null, System.getProperty("dataverse.files.test.secret-key")));
        // And we get an unsigned URL with the right stuff with no key
        System.clearProperty("dataverse.files.test.secret-key");
        String unsignedURL = remoteIO.generateTemporaryDownloadUrl(null, null, null);
        assertTrue(unsignedURL.equals(System.getProperty("dataverse.files.test.base-url") + "/" + logoPath));
        // Once we've opened, we can get the file size (only works if the HEAD call to
        // the file URL works
        remoteIO.open(DataAccessOption.READ_ACCESS);
        assertTrue(remoteIO.getSize() > 0);
        // If we ask for the path for an aux file, it is correct
        assertTrue(Paths
                .get(System.getProperty("dataverse.files.file.directory", "/tmp/files"), pid, logoPath + ".auxobject")
                .equals(remoteIO.getAuxObjectAsPath("auxobject")));

    }

}
