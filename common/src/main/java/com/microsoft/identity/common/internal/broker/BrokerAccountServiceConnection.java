package com.microsoft.identity.common.internal.broker;

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

import android.content.ComponentName;
import android.os.IBinder;

import com.microsoft.aad.adal.IBrokerAccountService;
import com.microsoft.identity.client.IMicrosoftAuthService;
import com.microsoft.identity.common.internal.logging.Logger;

public class BrokerAccountServiceConnection implements android.content.ServiceConnection {
    private static final String TAG = MicrosoftAuthServiceConnection.class.getSimpleName();
    private IBrokerAccountService mBrokerAccountService;
    private BrokerAccountServiceFuture mBrokerAccountServiceFuture;

    public BrokerAccountServiceConnection(BrokerAccountServiceFuture future) {
        mBrokerAccountServiceFuture = future;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Logger.verbose(TAG, "BrokerAccountService is connected.");
        mBrokerAccountService = IBrokerAccountService.Stub.asInterface(service);
        mBrokerAccountServiceFuture.setBrokerAccountService(mBrokerAccountService);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.verbose(TAG, "BrokerAccountService is disconnected.");
    }
}