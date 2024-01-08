# API v4

## Manga details

Mostly taken from [here](https://github.com/xiaoyaocz/dmzj_flutter/blob/aac6ba3/lib/protobuf/comic/detail_response.proto).

```protobuf
syntax = "proto3";

package dmzj.comic;


message ComicDetailResponse {
    int32 Errno = 1;
    string Errmsg = 2;
    ComicDetailInfoResponse Data= 3;
}

message ComicDetailInfoResponse {
    int32 Id = 1;
    string Title = 2;
    int32 Direction=3;
    int32 Islong=4;
    int32 IsDmzj=5;
    string Cover=6;
    string Description=7;
    int64 LastUpdatetime=8;
    string LastUpdateChapterName=9;
    int32 Copyright=10;
    string FirstLetter=11;
    string ComicPy=12;
    int32 Hidden=13;
    int32 HotNum=14;
    int32 HitNum=15;
    int32 Uid=16;
    int32 IsLock=17;
    int32 LastUpdateChapterId=18;
    repeated ComicDetailTypeItemResponse Types=19;
    repeated ComicDetailTypeItemResponse Status=20;
    repeated ComicDetailTypeItemResponse Authors=21;
    int32 SubscribeNum=22;
    repeated ComicDetailChapterResponse Chapters=23;
    int32 IsNeedLogin=24;
    //object UrlLinks=25; { string name = 1; repeated object links = 2; }
    // link { int32 = 1; string name = 2; string uriOrApk = 3; string icon = 4; string packageName = 5; string apk = 6; int32 = 7; }
    int32 IsHideChapter=26;
    //repeated object DhUrlLinks=27; { string name = 1; }

}

message ComicDetailTypeItemResponse {
    int32 TagId = 1;
    string TagName = 2;
}

message ComicDetailChapterResponse {
    string Title = 1;
    repeated ComicDetailChapterInfoResponse Data=2;
}
message ComicDetailChapterInfoResponse {
    int32 ChapterId = 1;
    string ChapterTitle = 2;
    int64 Updatetime=3;
    int32 Filesize=4;
    int32 ChapterOrder=5;
}
```

## Ranking

Taken from [here](https://github.com/xiaoyaocz/dmzj_flutter/blob/e7f1b1e/lib/protobuf/comic/rank_list_response.proto).

```protobuf
syntax = "proto3";

package dmzj.comic;


message ComicRankListResponse {
    int32 Errno = 1;
    string Errmsg = 2;
    repeated ComicRankListItemResponse Data= 3;
}

message ComicRankListItemResponse {
    int32 ComicId = 1;
    string Title = 2;
    string Authors=3;
    string Status=4;
    string Cover=5;
    string Types=6;
    int64 LastUpdatetime=7;
    string LastUpdateChapterName=8;
    string ComicPy=9;
    int32 Num=10;
    int32 TagId=11;
    string ChapterName=12;
    int32 ChapterId=13;
}
```

## Chapter images

```kotlin
@Serializable
class ResponseDto<T>(
    @ProtoNumber(1) val code: Int?,
    @ProtoNumber(2) val message: String?,
    @ProtoNumber(3) val data: T?,
)

@Serializable
class ChapterImagesDto(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val mangaId: Int,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val order: Int,
    @ProtoNumber(5) val direction: Int,
    // initial letter is sometimes different from that in original URLs, see manga ID 56649
    @ProtoNumber(6) val lowResImages: List<String>,
    // page count of low-res images
    @ProtoNumber(7) val pageCount: Int?,
    @ProtoNumber(8) val images: List<String>,
    @ProtoNumber(9) val commentCount: Int,
)
```

# Unused legacy API

## Chapter images

```kotlin
val webviewPageListApiUrl = "https://m.dmzj.com/chapinfo"
GET("$webviewPageListApiUrl/${chapter.url}.html")
```

```kotlin
val oldPageListApiUrl = "http://api.m.dmzj.com" // this domain has an expired certificate
GET("$oldPageListApiUrl/comic/chapter/${chapter.url}.html")
```
