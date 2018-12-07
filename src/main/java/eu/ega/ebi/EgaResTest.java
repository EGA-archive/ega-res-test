/*
 * Copyright 2017 ELIXIR EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.ega.ebi;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author asenf
 */
public class EgaResTest {
    private static final int VERSION_MAJOR = 0;
    private static final int VERSION_MINOR = 1;

    private static void error(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * @param args
     *            the command line arguments
     */
    public static void main(String[] args)
            throws NoSuchAlgorithmException, KeyManagementException, InterruptedException {

        try {
            String discUrl = args[0];
            int files = Integer.parseInt(args[1]);
            int threads = Integer.parseInt(args[2]);
            int chunks = Integer.parseInt(args[3]);
            String fileId = args[4];
            long fileSize = Long.parseLong(args[5]);
            String checksum = args[6];

            LargeScaleTest lst = new LargeScaleTest("", "", discUrl, files, threads, chunks, fileId, fileSize,
                    checksum);

            lst.run();
        } catch (Exception ex) {
            Logger.getLogger(EgaResTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
