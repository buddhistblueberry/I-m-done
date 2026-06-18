"""
Known ad / tracker / popunder domains we drop when looking for streams.
This list is conservative — only domains that are *never* legitimate
video CDNs for the embed providers we use.
"""

AD_DOMAINS = {
    # popunders / ad networks
    "popcash.net", "popads.net", "propellerads.com", "propellerclick.com",
    "adsterra.com", "adskeeper.co.uk", "mgid.com", "revcontent.com",
    "taboola.com", "outbrain.com", "exoclick.com", "exosrv.com",
    "trafficjunky.net", "trafficjunky.com", "juicyads.com", "ero-advertising.com",
    "bidgear.com", "clickadu.com", "adnium.com", "adcash.com",
    "adsrvr.org", "tsyndicate.com", "adskpr.com", "yllix.com",
    "hilltopads.com", "sexad.net", "rotaban.ru", "popmyads.com",
    "ad-maven.com", "googletagmanager.com", "googlesyndication.com",
    "doubleclick.net", "google-analytics.com", "scorecardresearch.com",
    "mc.yandex.ru", "facebook.net", "facebook.com",
    # tracker
    "histats.com", "statcounter.com", "yandex.ru",
}


def is_ad_url(url: str) -> bool:
    u = url.lower()
    for d in AD_DOMAINS:
        if d in u:
            return True
    return False
