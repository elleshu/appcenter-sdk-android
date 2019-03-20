/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.utils.storage;

import com.microsoft.appcenter.utils.context.AuthTokenInfo;

import java.util.Date;

/**
 * Interface for storage that works with token.
 */
public interface AuthTokenStorage {

    /**
     * Stores token value along with the corresponding account id.
     *
     * @param token         auth token.
     * @param homeAccountId unique identifier of user.
     * @param expiresTimestamp time when token create.
     */
    void saveToken(String token, String homeAccountId, Date expiresTimestamp);

    /**
     * Retrieves token value.
     *
     * @return auth token.
     */
    String getToken();

    /**
     * Retrieves homeAccountId value.
     *
     * @return unique identifier of user.
     */
    String getHomeAccountId();

    /**
     *
     *
     * @return
     */
    AuthTokenInfo getOldestToken();

    /**
     *
     *
     * @param token
     */
    void removeToken(String token);
}
