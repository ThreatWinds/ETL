package utm.threatintelligence.enums;

/*Enum used to define the Feed Types implemented in ETL process*/
public enum FeedTypeEnum {
    TYPE_OSINT_CIRCL("OSINT_CIRCL"),
    TYPE_OSINT_BOTVRIJ("OSINT_BOTVRIJ"),
    TYPE_OSINT_DIJITAL_SIDE("OSINT_DIJITAL_SIDE"),
    TYPE_GITHUB_YARA("GITHUB_YARA"),
    TYPE_RFXN_YARA("RFXN_YARA"),
    // IP feeds
    TYPE_GENERIC_IP_LIST("GENERIC_IP_LIST"),
    TYPE_ABUSE_SSLIP_BLACKLIST("ABUSE_SSLIP_BLACKLIST"),
    TYPE_COMMENT_IP_LIST("COMMENT_IP_LIST"),
    TYPE_REPUTATION_ALIEN_VAULT("REPUTATION_ALIEN_VAULT"),
    TYPE_FEODOTRACKER_IP_BLOCKLIST("FEODOTRACKER_IP_BLOCKLIST"),
    TYPE_CYBERCURE_AI_IP("CYBERCURE_AI_IP"),
    TYPE_IP_SPAM_LIST("IP_SPAM_LIST"),
    TYPE_MALSILO_IP_LIST("MALSILO_IP_LIST"),
    // URL feeds
    TYPE_GENERIC_URL_LIST("GENERIC_URL_LIST"),
    TYPE_PHISHTANK_ONLINE_URL_LIST("PHISHTANK_ONLINE_URL_LIST"),
    TYPE_DIAMOND_FOX_URL_LIST("DIAMOND_FOX_URL_LIST"),
    TYPE_VXVAULT_URL_LIST("VXVAULT_URL_LIST"),
    TYPE_CYBERCURE_AI_URL_LIST("CYBERCURE_AI_URL_LIST"),
    TYPE_MALSILO_URL_LIST("MALSILO_URL_LIST"),
    TYPE_BENKOW_CC_URL_LIST("BENKOW_CC_URL_LIST"),
    TYPE_ZIP_HAUS_ABUSE_URL_LIST("ZIP_HAUS_ABUSE_URL_LIST"),
    // CVE feeds
    TYPE_GENERIC_CVE_LIST("GENERIC_CVE_LIST"),
    // Domain feeds
    TYPE_MALSILO_DOMAIN_LIST("MALSILO_DOMAIN_LIST"),
    // MD5 hashes
    TYPE_ZIP_WITH_GENERIC_MD5_LIST("ZIP_WITH_GENERIC_MD5_LIST"),
    UNRECOGNIZED_FEED("UNRECOGNIZED_FEED");

    private String varName;

    private FeedTypeEnum(String varName) {
        this.varName = varName;
    }

    public String getVarValue() {
        return varName;
    }
}
