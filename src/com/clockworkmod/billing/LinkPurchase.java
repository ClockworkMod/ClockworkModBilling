package com.clockworkmod.billing;

public enum LinkPurchase {
    // require the purchase to have an account to link to.
    // allow cached emails.
    REQUIRE_EMAIL,

    // always prompt the user if they want to link.
    // one of the options will be "do not link".
    // this will not link purchases that are already linked
    // to market accounts, like in app billing.
    PROMPT_EMAIL,
    // prompt for market purchases too. this is handy
    // to collect buyer email addresses.
    PROMPT_EMAIL_INCLUDING_MARKET,

    // never prompt
    NO_PROMPT
}
