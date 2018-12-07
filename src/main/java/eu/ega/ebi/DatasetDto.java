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

/**
 *
 * @author asenf
 */
public class DatasetDto {
    private String datasetId;
    private String description;
    private String dacStableId;
    private String doubleSignature;

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }
    
    public String getDatasetId() {
        return this.datasetId;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDacStableId(String dacStableId) {
        this.dacStableId = dacStableId;
    }

    public void setDoubleSignature(String doubleSignature) {
        this.doubleSignature = doubleSignature;
    }
    
}
