from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ContentWarning(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    CONTENT_WARNING_UNSPECIFIED: _ClassVar[ContentWarning]
    CONTENT_WARNING_SAFE: _ClassVar[ContentWarning]
    CONTENT_WARNING_MIXED: _ClassVar[ContentWarning]
    CONTENT_WARNING_NSFW: _ClassVar[ContentWarning]
CONTENT_WARNING_UNSPECIFIED: ContentWarning
CONTENT_WARNING_SAFE: ContentWarning
CONTENT_WARNING_MIXED: ContentWarning
CONTENT_WARNING_NSFW: ContentWarning

class Index(_message.Message):
    __slots__ = ("name", "badgeLabel", "signingKey", "contact", "extensionList", "extensionListUrl")
    NAME_FIELD_NUMBER: _ClassVar[int]
    BADGELABEL_FIELD_NUMBER: _ClassVar[int]
    SIGNINGKEY_FIELD_NUMBER: _ClassVar[int]
    CONTACT_FIELD_NUMBER: _ClassVar[int]
    EXTENSIONLIST_FIELD_NUMBER: _ClassVar[int]
    EXTENSIONLISTURL_FIELD_NUMBER: _ClassVar[int]
    name: str
    badgeLabel: str
    signingKey: str
    contact: Contact
    extensionList: ExtensionList
    extensionListUrl: str
    def __init__(self, name: _Optional[str] = ..., badgeLabel: _Optional[str] = ..., signingKey: _Optional[str] = ..., contact: _Optional[_Union[Contact, _Mapping]] = ..., extensionList: _Optional[_Union[ExtensionList, _Mapping]] = ..., extensionListUrl: _Optional[str] = ...) -> None: ...

class Contact(_message.Message):
    __slots__ = ("website", "discord")
    WEBSITE_FIELD_NUMBER: _ClassVar[int]
    DISCORD_FIELD_NUMBER: _ClassVar[int]
    website: str
    discord: str
    def __init__(self, website: _Optional[str] = ..., discord: _Optional[str] = ...) -> None: ...

class ExtensionList(_message.Message):
    __slots__ = ("extensions",)
    EXTENSIONS_FIELD_NUMBER: _ClassVar[int]
    extensions: _containers.RepeatedCompositeFieldContainer[Extension]
    def __init__(self, extensions: _Optional[_Iterable[_Union[Extension, _Mapping]]] = ...) -> None: ...

class Extension(_message.Message):
    __slots__ = ("name", "packageName", "resources", "extensionLib", "versionCode", "versionName", "contentWarning", "sources")
    NAME_FIELD_NUMBER: _ClassVar[int]
    PACKAGENAME_FIELD_NUMBER: _ClassVar[int]
    RESOURCES_FIELD_NUMBER: _ClassVar[int]
    EXTENSIONLIB_FIELD_NUMBER: _ClassVar[int]
    VERSIONCODE_FIELD_NUMBER: _ClassVar[int]
    VERSIONNAME_FIELD_NUMBER: _ClassVar[int]
    CONTENTWARNING_FIELD_NUMBER: _ClassVar[int]
    SOURCES_FIELD_NUMBER: _ClassVar[int]
    name: str
    packageName: str
    resources: Resources
    extensionLib: str
    versionCode: int
    versionName: str
    contentWarning: ContentWarning
    sources: _containers.RepeatedCompositeFieldContainer[Source]
    def __init__(self, name: _Optional[str] = ..., packageName: _Optional[str] = ..., resources: _Optional[_Union[Resources, _Mapping]] = ..., extensionLib: _Optional[str] = ..., versionCode: _Optional[int] = ..., versionName: _Optional[str] = ..., contentWarning: _Optional[_Union[ContentWarning, str]] = ..., sources: _Optional[_Iterable[_Union[Source, _Mapping]]] = ...) -> None: ...

class Resources(_message.Message):
    __slots__ = ("apkUrl", "iconUrl")
    APKURL_FIELD_NUMBER: _ClassVar[int]
    ICONURL_FIELD_NUMBER: _ClassVar[int]
    apkUrl: str
    iconUrl: str
    def __init__(self, apkUrl: _Optional[str] = ..., iconUrl: _Optional[str] = ...) -> None: ...

class Source(_message.Message):
    __slots__ = ("id", "name", "language", "homeUrl", "mirrorUrls", "message")
    ID_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_FIELD_NUMBER: _ClassVar[int]
    HOMEURL_FIELD_NUMBER: _ClassVar[int]
    MIRRORURLS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    id: int
    name: str
    language: str
    homeUrl: str
    mirrorUrls: _containers.RepeatedScalarFieldContainer[str]
    message: str
    def __init__(self, id: _Optional[int] = ..., name: _Optional[str] = ..., language: _Optional[str] = ..., homeUrl: _Optional[str] = ..., mirrorUrls: _Optional[_Iterable[str]] = ..., message: _Optional[str] = ...) -> None: ...
