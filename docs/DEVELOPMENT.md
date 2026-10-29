# 开发说明

## 工程结构

```
app/src/main/java/cn/hautpocket/app/
  domain/  课程、表计模型，课表文件解析、导入策略与导出
  data/    本机存储、Android Keystore账号加密、只读水电客户端
  ui/      Jetpack Compose界面和导入预览
  MainActivity.kt
  OfficialWebActivity.kt
app/src/test/  合成数据、单元测试、课表互通样例
docs/         接口来源、功能边界、验证记录
tools/        Windows工具链、构建、打包及课表互通脚本
```

## 构建

使用JDK17、Android SDK35、Gradle8.9。准备 `local.properties` 指定SDK路径，然后运行：

```shell
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows也可按README使用便携工具链脚本。Windows上的Java AF_UNIX临时目录兼容处理仅在 `tools/build.ps1` 内，对系统Java配置没有修改。

`tools/package.ps1` 供维护者在本地生成交付包。自动检查包括29项JVM单元测试、Android编译和lint；实际设备、学校账号登录与水电查询需要另行联调，详见 [验证记录](VERIFICATION.md)。

## 课表互通验证

常规Gradle测试直接读取仓库内的合成MD样例，不依赖另一份项目。需要重新生成样例或验证反向导出时，将 [大学课表](https://github.com/rainthink-vanilla/univ-timetable) 放在相邻 `univ-timetable-public` 目录并安装其TypeScript依赖，再运行：

```shell
node tools/timetable-interop.cjs --generate
./gradlew :app:testDebugUnitTest
node tools/timetable-interop.cjs --verify
```

脚本执行对方真实的文件导出器和解析器，只使用合成课程，不读取个人课表或学校账号。

## 贡献

- 真实账号、密码、Cookie、token、roomverify和设备私钥不要写入代码、Issue或提交。
- 接口变动请提供脱敏的字段结构及错误状态，注明校区和官方入口；不要用虚构余额代替未接通的数据。
- 课表与水电的解析/计算变更应带有能覆盖实际边界的测试。
- 尊重上游许可。ZZUPocket及其他参考项目的来源与声明见 [第三方声明](../THIRD_PARTY_NOTICES.md)。
