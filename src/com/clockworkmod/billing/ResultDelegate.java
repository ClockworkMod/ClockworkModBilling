package com.clockworkmod.billing;

import java.io.Serializable;
import java.util.UUID;

import android.content.Intent;

import com.paypal.android.MEP.PayPalResultDelegate;

public class ResultDelegate implements PayPalResultDelegate, Serializable {
    public static final String SUCCEEDED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".SUCCEEDED";
    public static final String FAILED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".FAILED";
    public static final String CANCELLED = ResultDelegate.class.getName() + "." + UUID.randomUUID().toString() + ".CANCELLED";

	private static final long serialVersionUID = 10001L;

	private void broadcast(String action) {
	    Intent intent = new Intent(action);
	    ClockworkModBillingClient.mInstance.mContext.sendBroadcast(intent);
	}
	
	/**
	 * Notification that the payment has been completed successfully.
	 * 
	 * @param payKey			the pay key for the payment
	 * @param paymentStatus		the status of the transaction
	 */
	public void onPaymentSucceeded(String payKey, String paymentStatus) {
	    broadcast(SUCCEEDED);
	}

	/**
	 * Notification that the payment has failed.
	 * 
	 * @param paymentStatus		the status of the transaction
	 * @param correlationID		the correlationID for the transaction failure
	 * @param payKey			the pay key for the payment
	 * @param errorID			the ID of the error that occurred
	 * @param errorMessage		the error message for the error that occurred
	 */
	public void onPaymentFailed(String paymentStatus, String correlationID,
			String payKey, String errorID, String errorMessage) {
	       broadcast(FAILED);
	}

	/**
	 * Notification that the payment was canceled.
	 * 
	 * @param paymentStatus		the status of the transaction
	 */
	public void onPaymentCanceled(String paymentStatus) {
        broadcast(CANCELLED);
	}
}