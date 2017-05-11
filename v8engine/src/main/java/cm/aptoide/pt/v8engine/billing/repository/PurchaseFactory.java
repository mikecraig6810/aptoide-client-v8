/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 25/08/2016.
 */

package cm.aptoide.pt.v8engine.billing.repository;

import cm.aptoide.pt.v8engine.billing.inapp.InAppBillingSerializer;
import cm.aptoide.pt.model.v3.InAppBillingPurchasesResponse;
import cm.aptoide.pt.model.v3.PaidApp;
import cm.aptoide.pt.v8engine.billing.Purchase;
import java.io.IOException;

public class PurchaseFactory {

  private final InAppBillingSerializer serializer;

  public PurchaseFactory(InAppBillingSerializer serializer) {
    this.serializer = serializer;
  }

  public Purchase create(InAppBillingPurchasesResponse.InAppBillingPurchase purchase,
      String purchaseSignature) {
    return new Purchase() {

      @Override public String getData() throws IOException {
        return serializer.serializePurchase(purchase);
      }

      @Override public String getSignature() {
        return purchaseSignature;
      }
    };
  }

  public Purchase create(PaidApp app) {
    return new Purchase() {

      @Override public String getData() {
        return app.getPath().getStringPath();
      }

      @Override public String getSignature() {
        return null;
      }
    };
  }
}
