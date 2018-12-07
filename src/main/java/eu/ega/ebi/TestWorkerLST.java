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

import com.google.common.io.CountingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 *
 * @author asenf
 */
public class TestWorkerLST implements Runnable {

    private OkHttpClient client = null;
    
    private int workerId = -1;

    private DownloaderFile df;
    private int chunks;
    private String url;
    private String header;
    private long[] chunkStart = null;
    private long[] chunkEnd = null;
    
    private boolean success, done;
    private long chunkSize;
    
    public TestWorkerLST(int workerId, OkHttpClient client,
            DownloaderFile df, int chunks, String url,
            String header) {
        this.workerId = workerId;
        this.client = client;
        this.df = df;
        this.chunks = chunks;
        this.url = url;
        this.header = header;
        this.done = false;
        
        // chunk calculation
        if (df.getFileSize() < (1024L*1024L)) {
            this.chunks = 1;
        }
        this.chunkSize = (df.getFileSize()) / this.chunks;
        this.chunkStart = new long[this.chunks];
        this.chunkEnd = new long[this.chunks];
        long cntr = 0;
        for (int i=0; i<this.chunks; i++) {
            this.chunkStart[i] = cntr; // incl
            cntr = (cntr+chunkSize > (this.df.getFileSize()) ) ?
                    this.df.getFileSize() :
                    cntr+chunkSize;
            this.chunkEnd[i] = cntr; // excl
        }
    }

    @Override
    public void run() {
        System.out.println("Worker " + workerId + " started!  " + url + "   size: " + (this.df.getFileSize()) + "  testing chunks: " + this.chunks);

        // (1) Stream File et MDs
        Request requestRequest = null;        
        requestRequest = (this.header==null) ? 
                new Request.Builder()
                    .url(this.url)
                    .build() :
                new Request.Builder()
                    .url(this.url)
                    .addHeader("Authorization", this.header)
                    .build();
        Response response = null;
        try {
            response = client.newCall(requestRequest).execute();
        } catch (IOException ex) {
                System.out.println("An Error has occurred in Thread " + workerId
                    + ": " + ex.toString());
                System.out.println("\t\t" + url);
                response.close();
                return;
        }
        
        ResponseBody body = response.body();
        final InputStream inputStream = body.byteStream();

        // Set up Digest
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {}
        
        // Read Content for Length and MD5
        CountingInputStream cIn = new CountingInputStream(inputStream);
        DigestInputStream dIn = new DigestInputStream(cIn, md);        

        // Chunking
        MessageDigest[] chunkMD5s = new MessageDigest[this.chunks];
        for (int i=0; i<this.chunks; i++) {
            try {
                chunkMD5s[i] = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ex) {;}
        }
        
        // NullOutputStream
        OutputStream nullOutputStream = new OutputStream() { 
                        @Override public void write(int b) { } 
                        @Override public void write(byte[] b) { } 
                    };
        OutputStream nullDigestOutputStream = new OutputStream() {
                    long cnt = 0;
                    
                        @Override public void write(int b) { 
                            System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
                        } 
                        @Override public void write(byte[] b) {
                            int chunkIdx = (int) (cnt / chunkSize);
                            chunkIdx = (chunkIdx>=chunks)?chunkIdx-1:chunkIdx;
                            int write1 = (int) (( cnt+b.length > chunkEnd[chunkIdx] ) ?
                                    chunkEnd[chunkIdx] - cnt :
                                    b.length);
                            chunkMD5s[chunkIdx].update(b, 0, write1);
                            if (write1 < b.length && (chunkIdx+1) < chunks) {
                                int write2 = b.length - write1;
                                //chunkMD5s[chunkIdx+1].update(b, write1, write2);                            
                                byte[] b_ = new byte[write2];
                                System.arraycopy(b, write1, b_, 0, write2);
                                chunkMD5s[chunkIdx+1].update(b_, 0, write2);
                            }
                            cnt += b.length;
                        } 
                    };
        // Read
        long delta = System.currentTimeMillis();
        try {
            //IOUtils.copy(dIn, nullOutputStream);
            
            byte[] b = new byte[10485760];
            int s = dIn.read(b);
            while (s>-1) {
                byte[] b_ = new byte[s];
                System.arraycopy(b, 0, b_, 0, s);
                nullDigestOutputStream.write(b_);
                s = dIn.read(b);
            }
            
        } catch (IOException ex) {
           System.out.println("Error " + ex.toString());
        }
        delta = System.currentTimeMillis() - delta;
        
        // Assess Result
        long count = cIn.getCount();
        
        try {
            dIn.close();
        } catch (IOException ex) {}
        BigInteger bigInt = new BigInteger(1,md.digest());
        String mDigest = bigInt.toString(16);
        while(mDigest.length() < 32 ){
          mDigest = "0"+mDigest;
        }
        boolean match = true;
        if (this.df.getChecksum()!=null) {
            match = mDigest.equalsIgnoreCase(this.df.getChecksum());
            if (!match) {
                System.out.println("MISMATCH mDigest: "+mDigest+", MD5: "+this.df.getChecksum()+", ID:"+url);
                success = false;
            } else {
                System.out.println("MATCH mDigest: "+mDigest+", MD5: "+this.df.getChecksum()+", ID:"+url);
                success = true;
            }
        }
        
        body.close();
        response.close();
        this.done = true;

        // Done
        double speed = ((count/1024.0/1024.0)/(delta * 1.0))*1000.0;
        System.out.println("Thread " + workerId + " done in " + 
                           delta + " ms transferring " + count + " bytes. " +
                           "Speed: " + speed + " MB/s  Match? " + match);
        
        if (chunks > 1)
            multiStream(chunkMD5s);
    }
    
    private void multiStream(MessageDigest[] chunkMD5s) {
        
        System.out.println("----------Multi Segment Test!");
        for (int i=0; i<chunkMD5s.length; i++) {
            System.out.println("------------Segment " + (i+1));
            BigInteger bigInt = new BigInteger(1,chunkMD5s[i].digest());
            String mDigest = bigInt.toString(16);
            while(mDigest.length() < 32 ){
              mDigest = "0"+mDigest;
            }

            String url_ = url + 
                    "&startCoordinate=" + this.chunkStart[i] +
                    "&endCoordinate=" + this.chunkEnd[i];
System.out.println("URL " + url_);

            Request requestRequest = null;                    
            requestRequest = (this.header==null) ? 
                    new Request.Builder()
                        .url(url_)
                        .build() :
                    new Request.Builder()
                        .url(url_)
                        .addHeader("Authorization", this.header)
                        .build();
            //requestRequest = new Request.Builder()
            //    .url(url_)
            //    .build();
            Response response = null;
            try {
                response = client.newCall(requestRequest).execute();
            } catch (IOException ex) {
                    System.out.println("An Error has occurred in Thread (2) " + workerId
                        + ": " + ex.toString());
                    System.out.println("\t\t" + url);
                    response.close();
                    return;
            }

            ResponseBody body = response.body();
            final InputStream inputStream = body.byteStream();

            // Set up Digest
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ex) {}

            // Read Content for Length and MD5
            CountingInputStream cIn = new CountingInputStream(inputStream);
            DigestInputStream dIn = new DigestInputStream(cIn, md);        

            // NullOutputStream
            OutputStream nullOutputStream = new OutputStream() { 
                            @Override public void write(int b) { } 
                            @Override public void write(byte[] b) { } 
                        };
            
            // Read
            long delta = System.currentTimeMillis();
            try {
                //IOUtils.copy(dIn, nullOutputStream);

                byte[] b = new byte[10485760];
                int s = dIn.read(b);
                while (s>-1) {
                    nullOutputStream.write(b);
                    s = dIn.read(b);
                }

            } catch (IOException ex) {
               System.out.println("Error " + ex.toString());
            }
            delta = System.currentTimeMillis() - delta;

            // Assess Result
            long count = cIn.getCount();

            try {
                dIn.close();
            } catch (IOException ex) {}
            BigInteger bigInt_ = new BigInteger(1,md.digest());
            String mDigest_ = bigInt_.toString(16);
            while(mDigest_.length() < 32 ){
              mDigest_ = "0"+mDigest_;
            }
            
            boolean match = true;
            if (this.df.getChecksum()!=null) {
                match = mDigest.equalsIgnoreCase(mDigest_);
                if (!match) {
                    System.out.println("CHUNK MISMATCH mDigest: "+mDigest+", mDigest_: "+mDigest_+", chunk: " + i + ", ID:"+url_);
                    success = false;
                } else {
                    System.out.println("CHUNK MATCH mDigest: "+mDigest+", mDigest_: "+mDigest_+", chunk: " + i + ", ID:"+url_);
                    success = true;
                }
            }

            body.close();
            response.close();
            this.done = true;

            // Done
            double speed = ((count/1024.0/1024.0)/(delta * 1.0))*1000.0;
            System.out.println("Thread " + workerId + " Chunk " + i + " done in " + 
                               delta + " ms transferring " + count + " bytes. " +
                               "Speed: " + speed + " MB/s  Match? " + match);
        }
        
        
    }
    
    public DownloaderFile getDf() {
        return this.df;
    }
    
    public boolean getSuccess() {
        return this.success;
    }
    
    public String getUrl() {
        return this.url;
    }
    
    public int getChunks() {
        return this.chunks;
    }
    
    public int getWorkerId() {
        return this.workerId;
    }
    
    public boolean isDone() {
        return this.done;
    }
}
