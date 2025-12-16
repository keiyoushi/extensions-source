@file:Suppress("ArrayInDataClass", "unused", "PropertyName", "SpellCheckingInspection")

// Auto-generated with https://github.com/Dogacel/kotlinx-protobuf-gen

package eu.kanade.tachiyomi.extension.en.kagane.wv

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List

@Serializable
data class LicenseIdentification(
    @ProtoNumber(number = 1)
    val requestId: ByteArray? = null,
    @ProtoNumber(number = 2)
    val sessionId: ByteArray? = null,
    @ProtoNumber(number = 3)
    val purchaseId: ByteArray? = null,
    @ProtoNumber(number = 4)
    val type: LicenseType? = null,
    @ProtoNumber(number = 5)
    val version: Int? = null,
    @ProtoNumber(number = 6)
    val providerSessionToken: ByteArray? = null,
)

@Serializable
data class License(
    @ProtoNumber(number = 1)
    val id: LicenseIdentification? = null,
    @ProtoNumber(number = 2)
    val policy: Policy? = null,
    @ProtoNumber(number = 3)
    val key: List<KeyContainer?> = emptyList(),
    @ProtoNumber(number = 4)
    val licenseStartTime: Long? = null,
    @ProtoNumber(number = 5)
    val remoteAttestationVerified: Boolean? = null,
    @ProtoNumber(number = 6)
    val providerClientToken: ByteArray? = null,
    @ProtoNumber(number = 7)
    val protectionScheme: UInt? = null,
    @ProtoNumber(number = 8)
    val srmRequirement: ByteArray? = null,
    @ProtoNumber(number = 9)
    val srmUpdate: ByteArray? = null,
    @ProtoNumber(number = 10)
    val platformVerificationStatus: PlatformVerificationStatus? = null,
    @ProtoNumber(number = 11)
    val groupIds: List<ByteArray> = emptyList(),
) {
    @Serializable
    data class Policy(
        @ProtoNumber(number = 1)
        val canPlay: Boolean? = null,
        @ProtoNumber(number = 2)
        val canPersist: Boolean? = null,
        @ProtoNumber(number = 3)
        val canRenew: Boolean? = null,
        @ProtoNumber(number = 4)
        val rentalDurationSeconds: Long? = null,
        @ProtoNumber(number = 5)
        val playbackDurationSeconds: Long? = null,
        @ProtoNumber(number = 6)
        val licenseDurationSeconds: Long? = null,
        @ProtoNumber(number = 7)
        val renewalRecoveryDurationSeconds: Long? = null,
        @ProtoNumber(number = 8)
        val renewalServerUrl: String? = null,
        @ProtoNumber(number = 9)
        val renewalDelaySeconds: Long? = null,
        @ProtoNumber(number = 10)
        val renewalRetryIntervalSeconds: Long? = null,
        @ProtoNumber(number = 11)
        val renewWithUsage: Boolean? = null,
        @ProtoNumber(number = 12)
        val alwaysIncludeClientId: Boolean? = null,
        @ProtoNumber(number = 13)
        val playStartGracePeriodSeconds: Long? = null,
        @ProtoNumber(number = 14)
        val softEnforcePlaybackDuration: Boolean? = null,
        @ProtoNumber(number = 15)
        val softEnforceRentalDuration: Boolean? = null,
    )

    @Serializable
    data class KeyContainer(
        @ProtoNumber(number = 1)
        val id: ByteArray? = null,
        @ProtoNumber(number = 2)
        val iv: ByteArray? = null,
        @ProtoNumber(number = 3)
        val key: ByteArray? = null,
        @ProtoNumber(number = 4)
        val type: KeyType? = null,
        @ProtoNumber(number = 5)
        val level: SecurityLevel? = null,
        @ProtoNumber(number = 6)
        val requiredProtection: OutputProtection? = null,
        @ProtoNumber(number = 7)
        val requestedProtection: OutputProtection? = null,
        @ProtoNumber(number = 8)
        val keyControl: KeyControl? = null,
        @ProtoNumber(number = 9)
        val operatorSessionKeyPermissions: OperatorSessionKeyPermissions? = null,
        @ProtoNumber(number = 10)
        val videoResolutionConstraints: List<VideoResolutionConstraint?> = emptyList(),
        @ProtoNumber(number = 11)
        val antiRollbackUsageTable: Boolean? = null,
        @ProtoNumber(number = 12)
        val trackLabel: String? = null,
    ) {
        @Serializable
        data class KeyControl(
            @ProtoNumber(number = 1)
            val keyControlBlock: ByteArray? = null,
            @ProtoNumber(number = 2)
            val iv: ByteArray? = null,
        )

        @Serializable
        data class OutputProtection(
            @ProtoNumber(number = 1)
            val hdcp: HDCP? = null,
            @ProtoNumber(number = 2)
            val cgmsFlags: CGMS? = null,
            @ProtoNumber(number = 3)
            val hdcpSrmRule: HdcpSrmRule? = null,
            @ProtoNumber(number = 4)
            val disableAnalogOutput: Boolean? = null,
            @ProtoNumber(number = 5)
            val disableDigitalOutput: Boolean? = null,
        ) {
            @Serializable
            enum class HDCP {
                @ProtoNumber(number = 0)
                HDCP_NONE,

                @ProtoNumber(number = 1)
                HDCP_V1,

                @ProtoNumber(number = 2)
                HDCP_V2,

                @ProtoNumber(number = 3)
                HDCP_V2_1,

                @ProtoNumber(number = 4)
                HDCP_V2_2,

                @ProtoNumber(number = 5)
                HDCP_V2_3,

                @ProtoNumber(number = 255)
                HDCP_NO_DIGITAL_OUTPUT,
            }

            @Serializable
            enum class CGMS {
                @ProtoNumber(number = 42)
                CGMS_NONE,

                @ProtoNumber(number = 0)
                COPY_FREE,

                @ProtoNumber(number = 2)
                COPY_ONCE,

                @ProtoNumber(number = 3)
                COPY_NEVER,
            }

            @Serializable
            enum class HdcpSrmRule {
                @ProtoNumber(number = 0)
                HDCP_SRM_RULE_NONE,

                @ProtoNumber(number = 1)
                CURRENT_SRM,
            }
        }

        @Serializable
        data class VideoResolutionConstraint(
            @ProtoNumber(number = 1)
            val minResolutionPixels: UInt? = null,
            @ProtoNumber(number = 2)
            val maxResolutionPixels: UInt? = null,
            @ProtoNumber(number = 3)
            val requiredProtection: OutputProtection? = null,
        )

        @Serializable
        data class OperatorSessionKeyPermissions(
            @ProtoNumber(number = 1)
            val allowEncrypt: Boolean? = null,
            @ProtoNumber(number = 2)
            val allowDecrypt: Boolean? = null,
            @ProtoNumber(number = 3)
            val allowSign: Boolean? = null,
            @ProtoNumber(number = 4)
            val allowSignatureVerify: Boolean? = null,
        )

        @Serializable
        enum class KeyType {
            @ProtoNumber(number = 1)
            SIGNING,

            @ProtoNumber(number = 2)
            CONTENT,

            @ProtoNumber(number = 3)
            KEY_CONTROL,

            @ProtoNumber(number = 4)
            OPERATOR_SESSION,

            @ProtoNumber(number = 5)
            ENTITLEMENT,

            @ProtoNumber(number = 6)
            OEM_CONTENT,
        }

        @Serializable
        enum class SecurityLevel {
            @ProtoNumber(number = 1)
            SW_SECURE_CRYPTO,

            @ProtoNumber(number = 2)
            SW_SECURE_DECODE,

            @ProtoNumber(number = 3)
            HW_SECURE_CRYPTO,

            @ProtoNumber(number = 4)
            HW_SECURE_DECODE,

            @ProtoNumber(number = 5)
            HW_SECURE_ALL,
        }
    }
}

@Serializable
data class LicenseRequest(
    @ProtoNumber(number = 1)
    val clientId: ClientIdentification? = null,
    @ProtoNumber(number = 2)
    val contentId: ContentIdentification? = null,
    @ProtoNumber(number = 3)
    val type: RequestType? = null,
    @ProtoNumber(number = 4)
    val requestTime: Long? = null,
    @ProtoNumber(number = 5)
    val keyControlNonceDeprecated: ByteArray? = null,
    @ProtoNumber(number = 6)
    val protocolVersion: ProtocolVersion? = null,
    @ProtoNumber(number = 7)
    val keyControlNonce: UInt? = null,
    @ProtoNumber(number = 8)
    val encryptedClientId: EncryptedClientIdentification? = null,
) {
    @Serializable
    data class ContentIdentification(
        @ProtoNumber(number = 1)
        val widevinePsshData: WidevinePsshData? = null,
        @ProtoNumber(number = 2)
        val webmKeyId: WebmKeyId? = null,
        @ProtoNumber(number = 3)
        val existingLicense: ExistingLicense? = null,
        @ProtoNumber(number = 4)
        val initData: InitData? = null,
    ) {
        init {
            require(
                listOfNotNull(
                    widevinePsshData,
                    webmKeyId,
                    existingLicense,
                    initData,
                ).size <= 1,
            ) { "Should only contain one of content_id_variant." }
        }

        @Serializable
        data class WidevinePsshData(
            @ProtoNumber(number = 1)
            val psshData: List<ByteArray> = emptyList(),
            @ProtoNumber(number = 2)
            val licenseType: LicenseType? = null,
            @ProtoNumber(number = 3)
            val requestId: ByteArray? = null,
        )

        @Serializable
        data class WebmKeyId(
            @ProtoNumber(number = 1)
            val header: ByteArray? = null,
            @ProtoNumber(number = 2)
            val licenseType: LicenseType? = null,
            @ProtoNumber(number = 3)
            val requestId: ByteArray? = null,
        )

        @Serializable
        data class ExistingLicense(
            @ProtoNumber(number = 1)
            val licenseId: LicenseIdentification? = null,
            @ProtoNumber(number = 2)
            val secondsSinceStarted: Long? = null,
            @ProtoNumber(number = 3)
            val secondsSinceLastPlayed: Long? = null,
            @ProtoNumber(number = 4)
            val sessionUsageTableEntry: ByteArray? = null,
        )

        @Serializable
        data class InitData(
            @ProtoNumber(number = 1)
            val initDataType: InitDataType? = null,
            @ProtoNumber(number = 2)
            val initData: ByteArray? = null,
            @ProtoNumber(number = 3)
            val licenseType: LicenseType? = null,
            @ProtoNumber(number = 4)
            val requestId: ByteArray? = null,
        ) {
            @Serializable
            enum class InitDataType {
                @ProtoNumber(number = 1)
                CENC,

                @ProtoNumber(number = 2)
                WEBM,
            }
        }
    }

    @Serializable
    enum class RequestType {
        @ProtoNumber(number = 1)
        NEW,

        @ProtoNumber(number = 2)
        RENEWAL,

        @ProtoNumber(number = 3)
        RELEASE,
    }
}

@Serializable
data class MetricData(
    @ProtoNumber(number = 1)
    val stageName: String? = null,
    @ProtoNumber(number = 2)
    val metricData: List<TypeValue?> = emptyList(),
) {
    @Serializable
    data class TypeValue(
        @ProtoNumber(number = 1)
        val type: MetricType? = null,
        @ProtoNumber(number = 2)
        val `value`: Long? = null,
    )

    @Serializable
    enum class MetricType {
        @ProtoNumber(number = 1)
        LATENCY,

        @ProtoNumber(number = 2)
        TIMESTAMP,
    }
}

@Serializable
data class VersionInfo(
    @ProtoNumber(number = 1)
    val licenseSdkVersion: String? = null,
    @ProtoNumber(number = 2)
    val licenseServiceVersion: String? = null,
)

@Serializable
data class SignedMessage(
    @ProtoNumber(number = 1)
    val type: MessageType? = null,
    @ProtoNumber(number = 2)
    val msg: ByteArray? = null,
    @ProtoNumber(number = 3)
    val signature: ByteArray? = null,
    @ProtoNumber(number = 4)
    val sessionKey: ByteArray? = null,
    @ProtoNumber(number = 5)
    val remoteAttestation: ByteArray? = null,
    @ProtoNumber(number = 6)
    val metricData: List<MetricData?> = emptyList(),
    @ProtoNumber(number = 7)
    val serviceVersionInfo: VersionInfo? = null,
    @ProtoNumber(number = 8)
    val sessionKeyType: SessionKeyType? = null,
    @ProtoNumber(number = 9)
    val oemcryptoCoreMessage: ByteArray? = null,
) {
    @Serializable
    enum class MessageType {
        @ProtoNumber(number = 1)
        LICENSE_REQUEST,

        @ProtoNumber(number = 2)
        LICENSE,

        @ProtoNumber(number = 3)
        ERROR_RESPONSE,

        @ProtoNumber(number = 4)
        SERVICE_CERTIFICATE_REQUEST,

        @ProtoNumber(number = 5)
        SERVICE_CERTIFICATE,

        @ProtoNumber(number = 6)
        SUB_LICENSE,

        @ProtoNumber(number = 7)
        CAS_LICENSE_REQUEST,

        @ProtoNumber(number = 8)
        CAS_LICENSE,

        @ProtoNumber(number = 9)
        EXTERNAL_LICENSE_REQUEST,

        @ProtoNumber(number = 10)
        EXTERNAL_LICENSE,
    }

    @Serializable
    enum class SessionKeyType {
        @ProtoNumber(number = 0)
        UNDEFINED,

        @ProtoNumber(number = 1)
        WRAPPED_AES_KEY,

        @ProtoNumber(number = 2)
        EPHERMERAL_ECC_PUBLIC_KEY,
    }
}

@Serializable
data class ClientIdentification(
    @ProtoNumber(number = 1)
    val type: TokenType? = null,
    @ProtoNumber(number = 2)
    val token: ByteArray? = null,
    @ProtoNumber(number = 3)
    val clientInfo: List<NameValue?> = emptyList(),
    @ProtoNumber(number = 4)
    val providerClientToken: ByteArray? = null,
    @ProtoNumber(number = 5)
    val licenseCounter: UInt? = null,
    @ProtoNumber(number = 6)
    val clientCapabilities: ClientCapabilities? = null,
    @ProtoNumber(number = 7)
    val vmpData: ByteArray? = null,
    @ProtoNumber(number = 8)
    val deviceCredentials: List<ClientCredentials?> = emptyList(),
) {
    @Serializable
    data class NameValue(
        @ProtoNumber(number = 1)
        val name: String? = null,
        @ProtoNumber(number = 2)
        val `value`: String? = null,
    )

    @Serializable
    data class ClientCapabilities(
        @ProtoNumber(number = 1)
        val clientToken: Boolean? = null,
        @ProtoNumber(number = 2)
        val sessionToken: Boolean? = null,
        @ProtoNumber(number = 3)
        val videoResolutionConstraints: Boolean? = null,
        @ProtoNumber(number = 4)
        val maxHdcpVersion: HdcpVersion? = null,
        @ProtoNumber(number = 5)
        val oemCryptoApiVersion: UInt? = null,
        @ProtoNumber(number = 6)
        val antiRollbackUsageTable: Boolean? = null,
        @ProtoNumber(number = 7)
        val srmVersion: UInt? = null,
        @ProtoNumber(number = 8)
        val canUpdateSrm: Boolean? = null,
        @ProtoNumber(number = 9)
        val supportedCertificateKeyType: List<CertificateKeyType> = emptyList(),
        @ProtoNumber(number = 10)
        val analogOutputCapabilities: AnalogOutputCapabilities? = null,
        @ProtoNumber(number = 11)
        val canDisableAnalogOutput: Boolean? = null,
        @ProtoNumber(number = 12)
        val resourceRatingTier: UInt? = null,
    ) {
        @Serializable
        enum class HdcpVersion {
            @ProtoNumber(number = 0)
            HDCP_NONE,

            @ProtoNumber(number = 1)
            HDCP_V1,

            @ProtoNumber(number = 2)
            HDCP_V2,

            @ProtoNumber(number = 3)
            HDCP_V2_1,

            @ProtoNumber(number = 4)
            HDCP_V2_2,

            @ProtoNumber(number = 5)
            HDCP_V2_3,

            @ProtoNumber(number = 255)
            HDCP_NO_DIGITAL_OUTPUT,
        }

        @Serializable
        enum class CertificateKeyType {
            @ProtoNumber(number = 0)
            RSA_2048,

            @ProtoNumber(number = 1)
            RSA_3072,

            @ProtoNumber(number = 2)
            ECC_SECP256R1,

            @ProtoNumber(number = 3)
            ECC_SECP384R1,

            @ProtoNumber(number = 4)
            ECC_SECP521R1,
        }

        @Serializable
        enum class AnalogOutputCapabilities {
            @ProtoNumber(number = 0)
            ANALOG_OUTPUT_UNKNOWN,

            @ProtoNumber(number = 1)
            ANALOG_OUTPUT_NONE,

            @ProtoNumber(number = 2)
            ANALOG_OUTPUT_SUPPORTED,

            @ProtoNumber(number = 3)
            ANALOG_OUTPUT_SUPPORTS_CGMS_A,
        }
    }

    @Serializable
    data class ClientCredentials(
        @ProtoNumber(number = 1)
        val type: TokenType? = null,
        @ProtoNumber(number = 2)
        val token: ByteArray? = null,
    )

    @Serializable
    enum class TokenType {
        @ProtoNumber(number = 0)
        KEYBOX,

        @ProtoNumber(number = 1)
        DRM_DEVICE_CERTIFICATE,

        @ProtoNumber(number = 2)
        REMOTE_ATTESTATION_CERTIFICATE,

        @ProtoNumber(number = 3)
        OEM_DEVICE_CERTIFICATE,
    }
}

@Serializable
data class EncryptedClientIdentification(
    @ProtoNumber(number = 1)
    val providerId: String? = null,
    @ProtoNumber(number = 2)
    val serviceCertificateSerialNumber: ByteArray? = null,
    @ProtoNumber(number = 3)
    val encryptedClientId: ByteArray? = null,
    @ProtoNumber(number = 4)
    val encryptedClientIdIv: ByteArray? = null,
    @ProtoNumber(number = 5)
    val encryptedPrivacyKey: ByteArray? = null,
)

@Serializable
data class DrmCertificate(
    @ProtoNumber(number = 1)
    val type: Type? = null,
    @ProtoNumber(number = 2)
    val serialNumber: ByteArray? = null,
    @ProtoNumber(number = 3)
    val creationTimeSeconds: UInt? = null,
    @ProtoNumber(number = 12)
    val expirationTimeSeconds: UInt? = null,
    @ProtoNumber(number = 4)
    val publicKey: ByteArray? = null,
    @ProtoNumber(number = 5)
    val systemId: UInt? = null,
    @ProtoNumber(number = 6)
    val testDeviceDeprecated: Boolean? = null,
    @ProtoNumber(number = 7)
    val providerId: String? = null,
    @ProtoNumber(number = 8)
    val serviceTypes: List<ServiceType> = emptyList(),
    @ProtoNumber(number = 9)
    val algorithm: Algorithm? = null,
    @ProtoNumber(number = 10)
    val rotId: ByteArray? = null,
    @ProtoNumber(number = 11)
    val encryptionKey: EncryptionKey? = null,
) {
    @Serializable
    data class EncryptionKey(
        @ProtoNumber(number = 1)
        val publicKey: ByteArray? = null,
        @ProtoNumber(number = 2)
        val algorithm: Algorithm? = null,
    )

    @Serializable
    enum class Type {
        @ProtoNumber(number = 0)
        ROOT,

        @ProtoNumber(number = 1)
        DEVICE_MODEL,

        @ProtoNumber(number = 2)
        DEVICE,

        @ProtoNumber(number = 3)
        SERVICE,

        @ProtoNumber(number = 4)
        PROVISIONER,
    }

    @Serializable
    enum class ServiceType {
        @ProtoNumber(number = 0)
        UNKNOWN_SERVICE_TYPE,

        @ProtoNumber(number = 1)
        LICENSE_SERVER_SDK,

        @ProtoNumber(number = 2)
        LICENSE_SERVER_PROXY_SDK,

        @ProtoNumber(number = 3)
        PROVISIONING_SDK,

        @ProtoNumber(number = 4)
        CAS_PROXY_SDK,
    }

    @Serializable
    enum class Algorithm {
        @ProtoNumber(number = 0)
        UNKNOWN_ALGORITHM,

        @ProtoNumber(number = 1)
        RSA,

        @ProtoNumber(number = 2)
        ECC_SECP256R1,

        @ProtoNumber(number = 3)
        ECC_SECP384R1,

        @ProtoNumber(number = 4)
        ECC_SECP521R1,
    }
}

@Serializable
data class SignedDrmCertificate(
    @ProtoNumber(number = 1)
    val drmCertificate: ByteArray? = null,
    @ProtoNumber(number = 2)
    val signature: ByteArray? = null,
    @ProtoNumber(number = 3)
    val signer: SignedDrmCertificate? = null,
    @ProtoNumber(number = 4)
    val hashAlgorithm: HashAlgorithmProto? = null,
)

@Serializable
data class WidevinePsshData(
    @ProtoNumber(number = 2)
    val keyIds: List<ByteArray> = emptyList(),
    @ProtoNumber(number = 4)
    val contentId: ByteArray? = null,
    @ProtoNumber(number = 7)
    val cryptoPeriodIndex: UInt? = null,
    @ProtoNumber(number = 9)
    val protectionScheme: UInt? = null,
    @ProtoNumber(number = 10)
    val cryptoPeriodSeconds: UInt? = null,
    @ProtoNumber(number = 11)
    val type: Type? = null,
    @ProtoNumber(number = 12)
    val keySequence: UInt? = null,
    @ProtoNumber(number = 13)
    val groupIds: List<ByteArray> = emptyList(),
    @ProtoNumber(number = 14)
    val entitledKeys: List<EntitledKey?> = emptyList(),
    @ProtoNumber(number = 15)
    val videoFeature: String? = null,
    @ProtoNumber(number = 1)
    val algorithm: Algorithm? = null,
    @ProtoNumber(number = 3)
    val provider: String? = null,
    @ProtoNumber(number = 5)
    val trackType: String? = null,
    @ProtoNumber(number = 6)
    val policy: String? = null,
    @ProtoNumber(number = 8)
    val groupedLicense: ByteArray? = null,
) {
    @Serializable
    data class EntitledKey(
        @ProtoNumber(number = 1)
        val entitlementKeyId: ByteArray? = null,
        @ProtoNumber(number = 2)
        val keyId: ByteArray? = null,
        @ProtoNumber(number = 3)
        val key: ByteArray? = null,
        @ProtoNumber(number = 4)
        val iv: ByteArray? = null,
        @ProtoNumber(number = 5)
        val entitlementKeySizeBytes: UInt? = null,
    )

    @Serializable
    enum class Type {
        @ProtoNumber(number = 0)
        SINGLE,

        @ProtoNumber(number = 1)
        ENTITLEMENT,

        @ProtoNumber(number = 2)
        ENTITLED_KEY,
    }

    @Serializable
    enum class Algorithm {
        @ProtoNumber(number = 0)
        UNENCRYPTED,

        @ProtoNumber(number = 1)
        AESCTR,
    }
}

@Serializable
data class FileHashes(
    @ProtoNumber(number = 1)
    val signer: ByteArray? = null,
    @ProtoNumber(number = 2)
    val signatures: List<Signature?> = emptyList(),
) {
    @Serializable
    data class Signature(
        @ProtoNumber(number = 1)
        val filename: String? = null,
        @ProtoNumber(number = 2)
        val testSigning: Boolean? = null,
        @ProtoNumber(number = 3)
        val SHA512Hash: ByteArray? = null,
        @ProtoNumber(number = 4)
        val mainExe: Boolean? = null,
        @ProtoNumber(number = 5)
        val signature: ByteArray? = null,
    )
}

@Serializable
enum class LicenseType {
    @ProtoNumber(number = 1)
    STREAMING,

    @ProtoNumber(number = 2)
    OFFLINE,

    @ProtoNumber(number = 3)
    AUTOMATIC,
}

@Serializable
enum class PlatformVerificationStatus {
    @ProtoNumber(number = 0)
    PLATFORM_UNVERIFIED,

    @ProtoNumber(number = 1)
    PLATFORM_TAMPERED,

    @ProtoNumber(number = 2)
    PLATFORM_SOFTWARE_VERIFIED,

    @ProtoNumber(number = 3)
    PLATFORM_HARDWARE_VERIFIED,

    @ProtoNumber(number = 4)
    PLATFORM_NO_VERIFICATION,

    @ProtoNumber(number = 5)
    PLATFORM_SECURE_STORAGE_SOFTWARE_VERIFIED,
}

@Serializable
enum class ProtocolVersion {
    @ProtoNumber(number = 20)
    VERSION_2_0,

    @ProtoNumber(number = 21)
    VERSION_2_1,

    @ProtoNumber(number = 22)
    VERSION_2_2,
}

@Serializable
enum class HashAlgorithmProto {
    @ProtoNumber(number = 0)
    HASH_ALGORITHM_UNSPECIFIED,

    @ProtoNumber(number = 1)
    HASH_ALGORITHM_SHA_1,

    @ProtoNumber(number = 2)
    HASH_ALGORITHM_SHA_256,

    @ProtoNumber(number = 3)
    HASH_ALGORITHM_SHA_384,
}
