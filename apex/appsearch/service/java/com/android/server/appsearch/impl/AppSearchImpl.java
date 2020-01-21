/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.appsearch.impl;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.icing.proto.DocumentProto;
import com.google.android.icing.proto.PropertyConfigProto;
import com.google.android.icing.proto.PropertyProto;
import com.google.android.icing.proto.SchemaProto;
import com.google.android.icing.proto.SchemaTypeConfigProto;

/**
 * Manages interaction with {@link FakeIcing} and other components to implement AppSearch
 * functionality.
 */
public final class AppSearchImpl {
    private final Context mContext;
    private final @UserIdInt int mUserId;
    private final FakeIcing mFakeIcing = new FakeIcing();

    AppSearchImpl(@NonNull Context context, @UserIdInt int userId) {
        mContext = context;
        mUserId = userId;
    }

    /**
     * Updates the AppSearch schema for this app.
     *
     * @param callingUid The uid of the app calling AppSearch.
     * @param origSchema The schema to set for this app.
     * @param forceOverride Whether to force-apply the schema even if it is incompatible. Documents
     *     which do not comply with the new schema will be deleted.
     */
    public void setSchema(int callingUid, @NonNull SchemaProto origSchema, boolean forceOverride) {
        // Rewrite schema type names to include the calling app's package and uid.
        String typePrefix = getTypePrefix(callingUid);
        SchemaProto.Builder schemaBuilder = origSchema.toBuilder();
        rewriteSchemaTypes(typePrefix, schemaBuilder);

        // TODO(b/145635424): Save in schema type map
        // TODO(b/145635424): Apply the schema to Icing and report results
    }

    /**
     * Rewrites all types mentioned in the given {@code schemaBuilder} to prepend
     * {@code typePrefix}.
     *
     * @param typePrefix The prefix to add
     * @param schemaBuilder The schema to mutate
     */
    @VisibleForTesting
    void rewriteSchemaTypes(
            @NonNull String typePrefix, @NonNull SchemaProto.Builder schemaBuilder) {
        for (int typeIdx = 0; typeIdx < schemaBuilder.getTypesCount(); typeIdx++) {
            SchemaTypeConfigProto.Builder typeConfigBuilder =
                    schemaBuilder.getTypes(typeIdx).toBuilder();

            // Rewrite SchemaProto.types.schema_type
            String newSchemaType = typePrefix + typeConfigBuilder.getSchemaType();
            typeConfigBuilder.setSchemaType(newSchemaType);

            // Rewrite SchemaProto.types.properties.schema_type
            for (int propertyIdx = 0;
                    propertyIdx < typeConfigBuilder.getPropertiesCount();
                    propertyIdx++) {
                PropertyConfigProto.Builder propertyConfigBuilder =
                        typeConfigBuilder.getProperties(propertyIdx).toBuilder();
                if (!propertyConfigBuilder.getSchemaType().isEmpty()) {
                    String newPropertySchemaType =
                            typePrefix + propertyConfigBuilder.getSchemaType();
                    propertyConfigBuilder.setSchemaType(newPropertySchemaType);
                    typeConfigBuilder.setProperties(propertyIdx, propertyConfigBuilder);
                }
            }

            schemaBuilder.setTypes(typeIdx, typeConfigBuilder);
        }
    }

    /**
     * Adds a document to the AppSearch index.
     *
     * @param callingUid The uid of the app calling AppSearch.
     * @param origDocument The document to index.
     */
    public void putDocument(int callingUid, @NonNull DocumentProto origDocument) {
        // Rewrite the type names to include the app's prefix
        String typePrefix = getTypePrefix(callingUid);
        DocumentProto.Builder documentBuilder = origDocument.toBuilder();
        rewriteDocumentTypes(typePrefix, documentBuilder);
        mFakeIcing.put(documentBuilder.build());
    }

    /**
     * Rewrites all types mentioned anywhere in {@code documentBuilder} to prepend
     * {@code typePrefix}.
     *
     * @param typePrefix The prefix to add
     * @param documentBuilder The document to mutate
     */
    @VisibleForTesting
    void rewriteDocumentTypes(
            @NonNull String typePrefix,
            @NonNull DocumentProto.Builder documentBuilder) {
        // Rewrite the type name to include the app's prefix
        String newSchema = typePrefix + documentBuilder.getSchema();
        documentBuilder.setSchema(newSchema);

        // Add namespace. If we ever allow users to set their own namespaces, this will have
        // to change to prepend the prefix instead of setting the whole namespace. We will also have
        // to store the namespaces in a map similar to the type map so we can rewrite queries with
        // empty namespaces.
        documentBuilder.setNamespace(typePrefix);

        // Recurse into derived documents
        for (int propertyIdx = 0;
                propertyIdx < documentBuilder.getPropertiesCount();
                propertyIdx++) {
            int documentCount = documentBuilder.getProperties(propertyIdx).getDocumentValuesCount();
            if (documentCount > 0) {
                PropertyProto.Builder propertyBuilder =
                        documentBuilder.getProperties(propertyIdx).toBuilder();
                for (int documentIdx = 0; documentIdx < documentCount; documentIdx++) {
                    DocumentProto.Builder derivedDocumentBuilder =
                            propertyBuilder.getDocumentValues(documentIdx).toBuilder();
                    rewriteDocumentTypes(typePrefix, derivedDocumentBuilder);
                    propertyBuilder.setDocumentValues(documentIdx, derivedDocumentBuilder);
                }
                documentBuilder.setProperties(propertyIdx, propertyBuilder);
            }
        }
    }

   /**
     * Returns a type prefix in a format like {@code com.example.package@1000/} or
     * {@code com.example.sharedname:5678@1000/}.
     */
    @NonNull
    private String getTypePrefix(int callingUid) {
        // For regular apps, this call will return the package name. If callingUid is an
        // android:sharedUserId, this value may be another type of name and have a :uid suffix.
        String callingUidName = mContext.getPackageManager().getNameForUid(callingUid);
        if (callingUidName == null) {
            // Not sure how this is possible --- maybe app was uninstalled?
            throw new IllegalStateException("Failed to look up package name for uid " + callingUid);
        }
        return callingUidName + "@" + mUserId + "/";
    }
}