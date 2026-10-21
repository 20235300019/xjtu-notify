# 交大通知（xjtu-notify）

西安交通大学通知聚合 App：定时抓取校内几个通知栏目，用大模型判断每条通知对你重不重要、会不会过期，只把需要你看的推给你。

**手机端运行，不需要电脑常开。**

## 原始作者

本项目的**原始版本由 [@zty142857](https://github.com/zty142857)（学长）编写**，本仓库是在其基础上的修改版本。

原始版本未公开，源码由作者直接提供，经作者同意后开源。

## 本版本改了什么

只加了一样东西：**公众号订阅源**。

原先只抓 5 个网页栏目（教务处教学通知、国际处、机械学院、实践教学中心、文治书院），公众号的文章进不来。这一版接入了 RSS 订阅（例如取阅 feed.zoro.build 这类把公众号转成 RSS 的服务），于是公众号也能一起进通知列表、一起交给模型判断。

改动集中在 6 个文件、7 处：

| 文件 | 改了什么 |
| --- | --- |
| `data/Models.kt` | `SiteDef` 增加 `WX`（公众号），`home` 留空 |
| `data/Settings.kt` | 新增设置项 `rss_url` |
| `parse/Sources.kt` | 新增 `WxSource`，解析 RSS 2.0 的 `<item>`；注册进 `Sources.all` |
| `fetch/FetchManager.kt` | `run()` 与 `crawl()` 增加 `rssUrl` / `homeOverride`；地址为空则跳过该源 |
| `ui/AppViewModel.kt` | 把设置里的订阅地址传下去 |
| `ui/Screens.kt` | 设置页新增「公众号订阅地址」输入框 |

两个实现细节值得记一下：

1. **订阅地址存在本机设置里，没有硬编码。** 否则 APK 一发出去，订阅凭证就等于公开了——APK 里藏不住字符串。
2. **公众号的日期是 RFC822 格式**（`Mon, 20 Oct 2026 08:00:00 GMT`），现有的 `normalizeDate` 只认 `2026-10-20` 这种写法，直接套会静默返回空、条目全被丢掉。`WxSource` 里单独写了 `dateOf()` 处理。

## 怎么用

1. 装 APK，打开
2. **设置里填一个 AI 服务**（OpenAI 兼容接口都行：DeepSeek / 通义 / 智谱 / Kimi / 硅基流动），填 Base URL、API Key、模型名
3. 如果你要用公众号那条线：把 RSS 订阅地址填进「公众号订阅地址」，留空则跳过
4. 点抓取

> ⚠️ 这个 App 靠大模型判断通知重不重要，所以**没有 API Key 不能工作**。费用按你自己的 key 结算，个人用每天几分钱的量级。

## 自己构建

需要 JDK 17 + Android SDK（compileSdk 35、build-tools 35.0.0）+ Gradle 8.7。

```bash
# 在项目根目录建 local.properties，指向你的 SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

依赖走的是阿里云镜像（见 `settings.gradle.kts`），国内构建不用翻墙。

## 已知问题

- 设置页各输入框只在首次渲染时读一次值（原项目既有设计）
- `ui/Screens.kt` 里有个未使用的 `settings` 变量（编译警告，无害）

## 隐私

- 通知数据、API Key、订阅地址全部存在手机本机（Room + DataStore）
- 抓到的通知正文会发给**你自己配置的**大模型服务，不会经过本项目
- 仓库里不含任何密钥

## 许可

**未定**。原始代码的授权状态以原作者为准；如需使用请先联系作者确认。
