/*
 * Copyright 2018 asenf.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 *
 * @author asenf
 */
public class LargeScaleTest implements Runnable {

    private String discoveryUrl;
    private int numFiles, numThreads, numChunks;
    private DownloaderFile df;

    public LargeScaleTest(String user, String pass, String discUrl, int nFiles, int nThreads, int nChunks, String fileId, long fileSize, String checksum) {
        this.discoveryUrl = discUrl;
        this.numFiles = nFiles;
        this.numThreads = nThreads;
        this.numChunks = nChunks;
        df= new DownloaderFile(fileId, fileSize, checksum);

    }

    /*
     * Execute Test
     */
    @Override
    public void run() {

        // Discover environment
        HashMap<String, List<String>> env = getEnv();

        // Get Files from random datasets
        // (1) Get all Datasets
        // String metaDUrl = env.get("DATA").get(0);
        // String url_ = metaDUrl+"datasets";
        // DatasetDto[] datasets = getDatasets(url_, null);

        /*
         * // (2) Get Files String metaFUrl = env.get("DOWNLOADER").get(0);
         * HashMap<String, DownloaderFile[]> all = new HashMap<>(); OkHttpClient client
         * = SSLUtilities.getUnsafeOkHttpClient(); int iMax = datasets.length > 1500 ?
         * 1500 : datasets.length; for (int i=0; i<iMax; i++) {
         * System.out.println("Getting Files [" + i + "] " +
         * datasets[i].getDatasetId()); DownloaderFile[] files = getFiles(client,
         * metaFUrl + "datasets/" + datasets[i].getDatasetId() + "/files"); if
         * (files!=null) all.put(datasets[i].getDatasetId(), files); }
         */
        // Run Tests!
        Random r = new Random();
        try {
            List<String> servers = env.get("RES2");
            String server = servers.get(r.nextInt(servers.size()));
            testServer(env, server, null);
        } catch (Throwable th) {
            System.out.println("Error: " + th.toString());
        }
    }

    /*
     * Testing Server
     */
    private void testServer(HashMap<String, List<String>> env,
            /* HashMap<String, DownloaderFile[]> all, */
            String server, String header_) throws Exception {

        OkHttpClient client = SSLUtilities.getUnsafeOkHttpClient();

        ArrayList<Future<?>> results = new ArrayList<>();
        ArrayList<TestWorkerLST> workers = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long totalVolume = 0;
        for (int i = 0; i < numFiles; i++) {
            totalVolume += df.getFileSize();
            String url = server + "file/archive/" + df.getFileId() + "?destinationFormat=plain";

            TestWorkerLST worker = new TestWorkerLST(i, client, df, this.numChunks, url, header_);
            workers.add(worker);
            results.add(executor.submit(worker));
        }

        long dt = System.currentTimeMillis();
        System.out.println("Wait for Completion");
        boolean wait = true;
        while (wait) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
            }
            wait = false;
            for (int j = 0; j < results.size(); j++) {
                if (!results.get(j).isDone())
                    wait = true;
            }
        }
        dt = System.currentTimeMillis() - dt;

        // Done
        System.out.println("Done");
        double speed = ((totalVolume / 1024.0 / 1024.0) / (dt * 1.0)) * 1000.0;
        System.out.println(dt + " ms for " + totalVolume + " bytes RES test. Aggregate: " + speed + " MB/s");
    }

    /*
     * ************************************************************************ ****
     * **** ************************************************************************
     */

    /*
     * Individual Data Functions
     */
    private HashMap<String, List<String>> getEnv() {

        HashMap<String, List<String>> env = new HashMap<>();
        OkHttpClient client = SSLUtilities.getUnsafeOkHttpClient();
        Request requestRequest = new Request.Builder().url(this.discoveryUrl).build();
        Response response = null;
        try {
            response = client.newCall(requestRequest).execute();
        } catch (IOException ex) {
            System.out.println("Discovery error: " + ex.toString());
            return null;
        }
        if (!response.isSuccessful()) {
            System.out.println(this.discoveryUrl + " :: " + response.code() + "  " + response.toString());
            return null;
        }
        ResponseBody body = response.body();
        BufferedReader br = new BufferedReader(body.charStream());
        String line;
        try {
            line = br.readLine().trim();
            String app = "";
            while (line != null && !line.equals("</applications>")) { // Read through entire response
                if (line.startsWith("<app>")) { // Extract Application Name
                    app = line.substring(5);
                    app = app.substring(0, app.indexOf("<"));
                }
                if (line.startsWith("<homePageUrl>")) { // Associated URL --> store in HashMap
                    String url = line.substring(13);
                    url = url.substring(0, url.indexOf("<"));

                    List<String> instances;
                    if (env.containsKey(app)) {
                        instances = env.get(app);
                    } else {
                        instances = new ArrayList<>();
                    }
                    instances.add(url);
                    env.put(app, instances);
                }

                line = br.readLine().trim();
            }
        } catch (IOException ex) {
            Logger.getLogger(LargeScaleTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("env is " + env);
        return env;
    }

    private DatasetDto[] getDatasets(String url, String header) {

        OkHttpClient client = SSLUtilities.getUnsafeOkHttpClient();
        Request requestRequest = (header == null) ? new Request.Builder().url(url).build()
                : new Request.Builder().url(url).addHeader("Authorization", header).build();
        Response response = null;
        try {
            response = client.newCall(requestRequest).execute();
        } catch (IOException ex) {
            System.out.println("Datasets error: " + ex.toString());
            return null;
        }
        if (!response.isSuccessful()) {
            System.out.println(url + " :: " + response.code() + "  " + response.toString());
            return null;
        }
        ResponseBody body = response.body();

        try {
            if (header == null) {
                ObjectMapper objectMapper = new ObjectMapper();
                DatasetDto[] myObjects = objectMapper.readValue(body.string(), DatasetDto[].class);
                return myObjects;
            } else {
                ObjectMapper objectMapper = new ObjectMapper();
                String[] myObjects = objectMapper.readValue(body.string(), String[].class);
                DatasetDto[] myEmObjects = new DatasetDto[myObjects.length];
                for (int i = 0; i < myObjects.length; i++) {
                    myEmObjects[i] = new DatasetDto();
                    myEmObjects[i].setDatasetId(myObjects[i]);
                }
                return myEmObjects;
            }
        } catch (IOException ex) {
            System.out.println("Datasets error: " + ex.toString());
            return null;
        }
    }

    private DownloaderFile[] getFiles(OkHttpClient client, String url) {

        Request requestRequest = new Request.Builder().url(url).build();
        Response response = null;
        try {
            response = client.newCall(requestRequest).execute();
        } catch (IOException ex) {
            System.out.println("Files error: " + ex.toString());
            return null;
        }
        if (!response.isSuccessful()) {
            System.out.println(url + " :: " + response.code() + "  " + response.toString());
            return null;
        }
        ResponseBody body = response.body();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            DownloaderFile[] myObjects = objectMapper.readValue(body.string(), DownloaderFile[].class);
            return myObjects;
        } catch (IOException ex) {
            System.out.println("File error: " + ex.toString());
            return null;
        }
    }

    /*
     * private DownloaderFile getRandomFile(long minSize, HashMap<String,
     * DownloaderFile[]> all) { DownloaderFile f = null;
     * 
     * Set<String> keySet = all.keySet(); // Datasets String[] keys =
     * keySet.toArray(new String[keySet.size()]);
     * 
     * // Pick a random CIP File from a Dataset (with minimum size) Random r = new
     * Random(); while (f==null) { int idx = r.nextInt(keys.length);
     * DownloaderFile[] filesIdx = all.get(keys[idx]);
     * 
     * if (filesIdx.length > 0) { int fIdx = r.nextInt(filesIdx.length); if
     * (filesIdx[fIdx].getFileName().toLowerCase().endsWith(".cip")) { if ( (
     * minSize == 0 ) || (minSize > 0 && filesIdx[fIdx].getFileSize() > minSize) ) {
     * f = filesIdx[fIdx]; break; } } } }
     * 
     * return f; }
     */
}
