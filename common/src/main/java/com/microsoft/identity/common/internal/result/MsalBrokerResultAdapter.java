//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files(the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions :
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.
package com.microsoft.identity.common.internal.result;

import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.microsoft.identity.common.adal.internal.AuthenticationConstants;
import com.microsoft.identity.common.adal.internal.util.HashMapExtensions;
import com.microsoft.identity.common.exception.ArgumentException;
import com.microsoft.identity.common.exception.BaseException;
import com.microsoft.identity.common.exception.ClientException;
import com.microsoft.identity.common.exception.ErrorStrings;
import com.microsoft.identity.common.exception.IntuneAppProtectionPolicyRequiredException;
import com.microsoft.identity.common.exception.ServiceException;
import com.microsoft.identity.common.exception.UiRequiredException;
import com.microsoft.identity.common.exception.UserCancelException;
import com.microsoft.identity.common.internal.broker.BrokerResult;
import com.microsoft.identity.common.internal.cache.ICacheRecord;
import com.microsoft.identity.common.internal.dto.AccessTokenRecord;
import com.microsoft.identity.common.internal.dto.IAccountRecord;
import com.microsoft.identity.common.internal.logging.Logger;
import com.microsoft.identity.common.internal.request.SdkType;
import com.microsoft.identity.common.internal.util.HeaderSerializationUtil;
import com.microsoft.identity.common.internal.util.ICacheRecordGsonAdapter;
import com.microsoft.identity.common.internal.util.StringUtil;

import org.json.JSONException;

import java.lang.reflect.Type;
import java.util.List;

import static com.microsoft.identity.common.adal.internal.AuthenticationConstants.Broker.BROKER_ACCOUNTS;
import static com.microsoft.identity.common.adal.internal.AuthenticationConstants.Broker.BROKER_DEVICE_MODE;

public class MsalBrokerResultAdapter implements IBrokerResultAdapter {

    private static final String TAG = MsalBrokerResultAdapter.class.getName();

    @Override
    public Bundle bundleFromAuthenticationResult(@NonNull final ILocalAuthenticationResult authenticationResult) {
        Logger.info(TAG, "Constructing result bundle from ILocalAuthenticationResult");

        final IAccountRecord accountRecord = authenticationResult.getAccountRecord();

        final AccessTokenRecord accessTokenRecord = authenticationResult.getAccessTokenRecord();

        final BrokerResult brokerResult = new BrokerResult.Builder()
                .tenantProfileRecords(authenticationResult.getCacheRecordWithTenantProfileData())
                .accessToken(authenticationResult.getAccessToken())
                .idToken(authenticationResult.getIdToken())
                .refreshToken(authenticationResult.getRefreshToken())
                .homeAccountId(accountRecord.getHomeAccountId())
                .localAccountId(accountRecord.getLocalAccountId())
                .userName(accountRecord.getUsername())
                .tokenType(accessTokenRecord.getAccessTokenType())
                .clientId(accessTokenRecord.getClientId())
                .familyId(authenticationResult.getFamilyId())
                .scope(accessTokenRecord.getTarget())
                .clientInfo(accountRecord.getClientInfo())
                .authority(accessTokenRecord.getAuthority())
                .environment(accessTokenRecord.getEnvironment())
                .tenantId(authenticationResult.getTenantId())
                .expiresOn(Long.parseLong(accessTokenRecord.getExpiresOn()))
                .extendedExpiresOn(Long.parseLong(accessTokenRecord.getExtendedExpiresOn()))
                .cachedAt(Long.parseLong(accessTokenRecord.getCachedAt()))
                .speRing(authenticationResult.getSpeRing())
                .refreshTokenAge(authenticationResult.getRefreshTokenAge())
                .success(true)
                .build();

        final Bundle resultBundle = new Bundle();
        resultBundle.putString(
                AuthenticationConstants.Broker.BROKER_RESULT_V2,
                new Gson().toJson(brokerResult, BrokerResult.class)
        );
        resultBundle.putBoolean(AuthenticationConstants.Broker.BROKER_REQUEST_V2_SUCCESS, true);

        return resultBundle;
    }

    @Override
    public Bundle bundleFromBaseException(@NonNull final BaseException exception) {
        Logger.info(TAG, "Constructing result bundle from BaseException");

        final BrokerResult.Builder builder = new BrokerResult.Builder()
                .success(false)
                .errorCode(exception.getErrorCode())
                .errorMessage(exception.getMessage())
                .correlationId(exception.getCorrelationId())
                .cliTelemErrorCode(exception.getCliTelemErrorCode())
                .cliTelemSubErrorCode(exception.getCliTelemSubErrorCode())
                .speRing(exception.getSpeRing())
                .refreshTokenAge(exception.getRefreshTokenAge());

        if (exception instanceof ServiceException) {
            builder.oauthSubErrorCode(((ServiceException) exception).getOAuthSubErrorCode())
                    .httpStatusCode(((ServiceException) exception).getHttpStatusCode())
                    .httpResponseHeaders(
                            HeaderSerializationUtil.toJson((
                                    (ServiceException) exception).getHttpResponseHeaders()
                            ))
                    .httpResponseBody(new Gson().toJson(
                            ((ServiceException) exception).getHttpResponseBody()));
        }

        if (exception instanceof IntuneAppProtectionPolicyRequiredException) {
            builder.userName(((IntuneAppProtectionPolicyRequiredException) exception).getAccountUpn())
                    .localAccountId(((IntuneAppProtectionPolicyRequiredException) exception).getAccountUserId())
                    .authority(((IntuneAppProtectionPolicyRequiredException) exception).getAuthorityUrl())
                    .tenantId(((IntuneAppProtectionPolicyRequiredException) exception).getTenantId());
        }

        final Bundle resultBundle = new Bundle();
        resultBundle.putString(
                AuthenticationConstants.Broker.BROKER_RESULT_V2,
                new Gson().toJson(builder.build(), BrokerResult.class)
        );
        resultBundle.putBoolean(AuthenticationConstants.Broker.BROKER_REQUEST_V2_SUCCESS, false);

        return resultBundle;
    }

    @Override
    public ILocalAuthenticationResult authenticationResultFromBundle(@NonNull final Bundle resultBundle) {
        final BrokerResult brokerResult = brokerResultFromBundle(resultBundle);

        if (brokerResult == null) {
            Logger.error(TAG, "Broker Result not returned from Broker, ", null);
            return null;
        }

        Logger.info(TAG, "Broker Result returned from Bundle, constructing authentication result");

        final List<ICacheRecord> tenantProfileCacheRecords = brokerResult.getTenantProfileData();
        final LocalAuthenticationResult authenticationResult = new LocalAuthenticationResult(
                tenantProfileCacheRecords.get(0),
                tenantProfileCacheRecords,
                SdkType.MSAL
        );

        return authenticationResult;

    }

    public static BrokerResult brokerResultFromBundle(@NonNull final Bundle resultBundle) {
        return new GsonBuilder()
                .registerTypeAdapter(ICacheRecord.class, new ICacheRecordGsonAdapter())
                .create()
                .fromJson(
                        resultBundle.getString(AuthenticationConstants.Broker.BROKER_RESULT_V2),
                        BrokerResult.class
                );
    }

    public static boolean getHelloResultFromBundle(final Bundle bundle) throws ClientException {
        final String methodName = ":getHelloResultFromBundle";
        if (bundle == null) {
            Logger.warn(TAG + methodName, "The hello result bundle is null.");
            return false;
        }

        if (!StringUtil.isEmpty(bundle.getString(AuthenticationConstants.Broker.NEGOTIATED_BP_VERSION_KEY))) {
            final String negotiatedBrokerProtocolVersion = bundle.getString(AuthenticationConstants.Broker.NEGOTIATED_BP_VERSION_KEY);
            Logger.info(TAG + methodName,
                    "Able to establish the connect, " +
                            "the broker protocol version in common is ["
                            + negotiatedBrokerProtocolVersion + "]");
            return true;
        }

        if (!StringUtil.isEmpty(bundle.getString(AuthenticationConstants.OAuth2.ERROR))
                && !StringUtil.isEmpty(bundle.getString(AuthenticationConstants.OAuth2.ERROR_DESCRIPTION))) {
            final String errorCode = bundle.getString(AuthenticationConstants.OAuth2.ERROR);
            final String errorMessage = bundle.getString(AuthenticationConstants.OAuth2.ERROR_DESCRIPTION);
            throw new ClientException(errorCode, errorMessage);
        }

        if (bundle.get(AuthenticationConstants.Broker.BROKER_RESULT_V2) != null
                && bundle.get(AuthenticationConstants.Broker.BROKER_RESULT_V2) instanceof BrokerResult) {
            // for the back compatibility purpose to version 3.0.4 and 3.0.6.
            final BrokerResult brokerResult = (BrokerResult) bundle.get(AuthenticationConstants.Broker.BROKER_RESULT_V2);
            throw new ClientException(brokerResult.getErrorCode(), brokerResult.getErrorMessage());
        }

        return false;
    }

    @Override
    public BaseException baseExceptionFromBundle(@NonNull final Bundle resultBundle) {
        Logger.info(TAG, "Constructing exception from result bundle");

        final BrokerResult brokerResult = brokerResultFromBundle(resultBundle);

        if (brokerResult == null) {
            Logger.error(TAG, "Broker Result not returned from Broker, ", null);
            return new BaseException(ErrorStrings.UNKNOWN_ERROR, "Broker Result not returned from Broker");

        }

        BaseException baseException;

        final String errorCode = brokerResult.getErrorCode();

        if (AuthenticationConstants.OAuth2ErrorCode.INTERACTION_REQUIRED.equalsIgnoreCase(errorCode) ||
                AuthenticationConstants.OAuth2ErrorCode.INVALID_GRANT.equalsIgnoreCase(errorCode)) {

            Logger.warn(TAG, "Received a UIRequired exception from Broker : " + errorCode);
            baseException = new UiRequiredException(
                    errorCode,
                    brokerResult.getErrorMessage()
            );

        } else if (AuthenticationConstants.OAuth2ErrorCode.UNAUTHORIZED_CLIENT.equalsIgnoreCase(errorCode) &&
                AuthenticationConstants.OAuth2SubErrorCode.PROTECTION_POLICY_REQUIRED.
                        equalsIgnoreCase(brokerResult.getSubErrorCode())) {

            Logger.warn(
                    TAG,
                    "Received a IntuneAppProtectionPolicyRequiredException exception from Broker : "
                            + errorCode);
            baseException = getIntuneProtectionRequiredException(brokerResult);

        } else if (ErrorStrings.USER_CANCELLED.equalsIgnoreCase(errorCode)) {

            Logger.warn(TAG, "Received a User cancelled exception from Broker : " + errorCode);
            baseException = new UserCancelException();

        } else if (ArgumentException.ILLEGAL_ARGUMENT_ERROR_CODE.equalsIgnoreCase(errorCode)) {

            Logger.warn(TAG, "Received a Argument exception from Broker : " + errorCode);
            baseException = new ArgumentException(
                    ArgumentException.ACQUIRE_TOKEN_OPERATION_NAME,
                    errorCode,
                    brokerResult.getErrorMessage()
            );

        } else if (!TextUtils.isEmpty(brokerResult.getHttpResponseHeaders()) ||
                !TextUtils.isEmpty(brokerResult.getHttpResponseBody())) {

            Logger.warn(TAG, "Received a Service exception from Broker : " + errorCode);
            baseException = getServiceException(brokerResult);

        } else {

            Logger.warn(TAG, "Received a Client exception from Broker : " + errorCode);
            baseException = new ClientException(
                    brokerResult.getErrorCode(),
                    brokerResult.getErrorMessage()
            );
        }

        baseException.setCliTelemErrorCode(brokerResult.getCliTelemErrorCode());
        baseException.setCliTelemSubErrorCode(brokerResult.getCliTelemSubErrorCode());
        baseException.setCorrelationId(brokerResult.getCorrelationId());
        baseException.setSpeRing(brokerResult.getSpeRing());
        baseException.setRefreshTokenAge(brokerResult.getRefreshTokenAge());

        return baseException;
    }

    /**
     * Helper method to retrieve IntuneAppProtectionPolicyRequiredException from BrokerResult
     */
    private IntuneAppProtectionPolicyRequiredException getIntuneProtectionRequiredException(
            @NonNull final BrokerResult brokerResult) {
        final IntuneAppProtectionPolicyRequiredException exception =
                new IntuneAppProtectionPolicyRequiredException(
                        brokerResult.getErrorCode(),
                        brokerResult.getErrorMessage()
                );
        exception.setTenantId(brokerResult.getTenantId());
        exception.setAuthorityUrl(brokerResult.getAuthority());
        exception.setAccountUserId(brokerResult.getLocalAccountId());
        exception.setAccountUpn(brokerResult.getUserName());
        exception.setOauthSubErrorCode(brokerResult.getSubErrorCode());
        try {
            exception.setHttpResponseBody(HashMapExtensions.jsonStringAsMap(
                    brokerResult.getHttpResponseBody())
            );
            if (brokerResult.getHttpResponseHeaders() != null) {
                exception.setHttpResponseHeaders(
                        HeaderSerializationUtil.fromJson(
                                brokerResult.getHttpResponseHeaders())
                );
            }
        } catch (JSONException e) {
            Logger.warn(TAG, "Unable to parse json");
        }
        return exception;
    }

    /**
     * Helper method to retrieve ServiceException from BrokerResult
     */
    private ServiceException getServiceException(@NonNull final BrokerResult brokerResult) {
        final ServiceException serviceException = new ServiceException(
                brokerResult.getErrorCode(),
                brokerResult.getErrorMessage(),
                null
        );

        serviceException.setOauthSubErrorCode(brokerResult.getSubErrorCode());

        try {
            serviceException.setHttpResponseBody(
                    brokerResult.getHttpResponseBody() != null ?
                            HashMapExtensions.jsonStringAsMap(
                                    brokerResult.getHttpResponseBody()) :
                            null
            );
            serviceException.setHttpResponseHeaders(
                    brokerResult.getHttpResponseHeaders() != null ?
                            HeaderSerializationUtil.fromJson(
                                    brokerResult.getHttpResponseHeaders()) :
                            null
            );

        } catch (JSONException e) {
            Logger.warn(TAG, "Unable to parse json");
        }
        return serviceException;

    }

    /**
     * Get a bundle from an Account Mode string.
     *
     * @param isSharedDevice true if this device is registered as shared. False otherwise.
     * @return Bundle
     */
    public Bundle bundleFromDeviceMode(@NonNull final boolean isSharedDevice) {
        final Bundle resultBundle = new Bundle();
        resultBundle.putBoolean(BROKER_DEVICE_MODE, isSharedDevice);
        return resultBundle;
    }

    /**
     * Get Device mode from bundle.
     *
     * @param bundle Bundle
     * @return Account mode.
     */
    public static boolean deviceModeFromBundle(@NonNull final Bundle bundle) {
        return bundle.getBoolean(BROKER_DEVICE_MODE);
    }

    /**
     * Get a bundle from account(s)'s List<ICacheRecord>.
     *
     * @param cacheRecords account(s)'s List<ICacheRecord>.
     * @return Bundle
     */
    public static Bundle bundleFromAccounts(@NonNull final List<ICacheRecord> cacheRecords) {
        final Bundle resultBundle = new Bundle();

        if (cacheRecords != null) {
            resultBundle.putString(BROKER_ACCOUNTS, getJsonStringFromICacheRecordList(cacheRecords));
        }

        return resultBundle;
    }

    public static String getJsonStringFromICacheRecordList(List<ICacheRecord> cacheRecords) {
        final Type listOfCacheRecords = new TypeToken<List<ICacheRecord>>() {
        }.getType();
        return new Gson().toJson(cacheRecords, listOfCacheRecords);
    }

    /**
     * Get current account's ICacheRecord from bundle.
     *
     * @param bundle Bundle
     * @return List<ICacheRecord> of the current account. This could be null.
     */
    public static List<ICacheRecord> accountsFromBundle(@NonNull final Bundle bundle) throws ClientException {
        final String accountJson = bundle.getString(BROKER_ACCOUNTS);

        if (accountJson == null) {
            throw new ClientException(ErrorStrings.NO_ACCOUNT_FOUND,
                    "No account found. The bundle does not contain the BROKER_ACCOUNTS value.");
        }

        return getICacheRecordListFromJsonString(accountJson);
    }

    public static List<ICacheRecord> getICacheRecordListFromJsonString(String accountJson) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(ICacheRecord.class, new ICacheRecordGsonAdapter());

        final Type listOfCacheRecords = new TypeToken<List<ICacheRecord>>() {
        }.getType();
        return builder.create().fromJson(accountJson, listOfCacheRecords);
    }
}
