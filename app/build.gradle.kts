import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Online-service configuration is intentionally separate from the first-upload build.
// New personal Play accounts should upload the `launchRelease` bundle first.
val adsEnabled = providers.gradleProperty("RIFT_ADS_ENABLED").orElse("false").get().toBoolean()
val billingEnabled = providers.gradleProperty("RIFT_BILLING_ENABLED").orElse("false").get().toBoolean()
val consumablePurchasesEnabled = providers.gradleProperty("RIFT_CONSUMABLE_PURCHASES_ENABLED").orElse("false").get().toBoolean()
val playGamesConfigured = providers.gradleProperty("RIFT_PLAY_GAMES_CONFIGURED").orElse("false").get().toBoolean()

val googleTestAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestRewardedId = "ca-app-pub-3940256099942544/5224354917"
val googleTestInterstitialId = "ca-app-pub-3940256099942544/1033173712"

val adMobAppId = providers.gradleProperty("RIFT_ADMOB_APP_ID").orElse(googleTestAppId).get()
val rewardedAdId = providers.gradleProperty("RIFT_REWARDED_AD_UNIT_ID").orElse(googleTestRewardedId).get()
val interstitialAdId = providers.gradleProperty("RIFT_INTERSTITIAL_AD_UNIT_ID").orElse(googleTestInterstitialId).get()
val privacyPolicyUrl = providers.gradleProperty("RIFT_PRIVACY_POLICY_URL").orElse("").get()
val playGamesProjectId = providers.gradleProperty("RIFT_PGS_PROJECT_ID").orElse("0").get()
val leaderboardHighScoreId = providers.gradleProperty("RIFT_PGS_LEADERBOARD_HIGH_SCORE").orElse("disabled_leaderboard").get()
val achievementIds = listOf(
    providers.gradleProperty("RIFT_PGS_ACH_FIRST_RIFT").orElse("disabled_achievement_1").get(),
    providers.gradleProperty("RIFT_PGS_ACH_WAVE_5").orElse("disabled_achievement_2").get(),
    providers.gradleProperty("RIFT_PGS_ACH_WAVE_10").orElse("disabled_achievement_3").get(),
    providers.gradleProperty("RIFT_PGS_ACH_HUNTER_100").orElse("disabled_achievement_4").get(),
    providers.gradleProperty("RIFT_PGS_ACH_HUNTER_1000").orElse("disabled_achievement_5").get(),
    providers.gradleProperty("RIFT_PGS_ACH_SCORE_10000").orElse("disabled_achievement_6").get(),
    providers.gradleProperty("RIFT_PGS_ACH_OVERDRIVE_5").orElse("disabled_achievement_7").get(),
    providers.gradleProperty("RIFT_PGS_ACH_COLLECTOR").orElse("disabled_achievement_8").get()
)

if (adsEnabled && (adMobAppId == googleTestAppId || rewardedAdId == googleTestRewardedId || interstitialAdId == googleTestInterstitialId)) {
    throw GradleException("RIFT_ADS_ENABLED=true requires your own AdMob App ID, Rewarded ID and Interstitial ID.")
}
if (playGamesConfigured && (playGamesProjectId == "0" || leaderboardHighScoreId.startsWith("disabled_") || achievementIds.any { it.startsWith("disabled_") })) {
    throw GradleException("RIFT_PLAY_GAMES_CONFIGURED=true requires the real Play Games project, leaderboard and all 8 achievement IDs.")
}
if (consumablePurchasesEnabled && !billingEnabled) {
    throw GradleException("RIFT_CONSUMABLE_PURCHASES_ENABLED=true requires RIFT_BILLING_ENABLED=true")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

android {
    namespace = "com.minhajul.riftreign"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minhajul.riftreign"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.0"

        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${privacyPolicyUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    flavorDimensions += "services"
    productFlavors {
        // FIRST PLAY UPLOAD / CLOSED TEST: no network, no ads, no Billing SDK, no Play Games SDK.
        // This minimizes policy/setup blockers for a brand-new personal developer account.
        create("launch") {
            dimension = "services"

            buildConfigField("boolean", "ADS_ENABLED", "false")
            buildConfigField("boolean", "BILLING_ENABLED", "false")
            buildConfigField("boolean", "CONSUMABLE_PURCHASES_ENABLED", "false")
            buildConfigField("boolean", "PLAY_GAMES_CONFIGURED", "false")
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"\"")
        }

        // FUTURE UPDATE: same package/signing identity, with Google online services once real IDs exist.
        create("online") {
            dimension = "services"
            manifestPlaceholders["adMobAppId"] = adMobAppId
            buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
            buildConfigField("boolean", "BILLING_ENABLED", billingEnabled.toString())
            buildConfigField("boolean", "CONSUMABLE_PURCHASES_ENABLED", consumablePurchasesEnabled.toString())
            buildConfigField("boolean", "PLAY_GAMES_CONFIGURED", playGamesConfigured.toString())
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$rewardedAdId\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"$interstitialAdId\"")
            resValue("string", "game_services_project_id", playGamesProjectId)
            resValue("string", "leaderboard_high_score", leaderboardHighScoreId)
            resValue("string", "achievement_first_rift", achievementIds[0])
            resValue("string", "achievement_wave_5", achievementIds[1])
            resValue("string", "achievement_wave_10", achievementIds[2])
            resValue("string", "achievement_hunter_100", achievementIds[3])
            resValue("string", "achievement_hunter_1000", achievementIds[4])
            resValue("string", "achievement_score_10000", achievementIds[5])
            resValue("string", "achievement_overdrive_5", achievementIds[6])
            resValue("string", "achievement_collector", achievementIds[7])
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val storePath = keystoreProperties.getProperty("storeFile")
                    ?: throw GradleException("keystore.properties is missing storeFile")
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: throw GradleException("keystore.properties is missing storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: throw GradleException("keystore.properties is missing keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: throw GradleException("keystore.properties is missing keyPassword")
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.fromTarget("17") }
}

dependencies {
    // Online SDKs are completely absent from launchRelease.
    "onlineImplementation"("com.android.billingclient:billing:9.1.0")
    "onlineImplementation"("com.google.android.gms:play-services-ads:25.4.0")
    "onlineImplementation"("com.google.android.ump:user-messaging-platform:4.0.0")
    "onlineImplementation"("com.google.android.gms:play-services-games-v2:22.0.0")
}
