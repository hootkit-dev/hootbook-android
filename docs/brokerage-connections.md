# 券商持仓连接（第一版：IBKR Flex）

## 使用方式

**添加账户 → 券商** 页面上方是 **连接券商自动同步**：每个已注册的提供方一行（目前仅 IBKR），
显示名称、是否已连接（账户 ID）以及「连接」；其下列出每个连接，可「断开」。同一页下方是 **手动记账**
表单（名称、券商、尾号、币种、余额），两者互不影响。

以下入口都打开这一页（券商类型已选好）：

- 账户列表（全部账户视图）中的投资分区：没有券商账户和连接时显示「连接券商或添加券商账户」，
  展开分区时末尾也有同一行；
- 首页账户分类中尚未有投资时的「连接」；
- 首页 → **投资** 右下角加号（只读持仓页本身仍从首页 → 投资进入）。

断开连接也可在 **设置 → 账户 → 投资** 分区的连接行完成。

## 投资账户如何识别

账户名称能匹配目录中的券商（`InstitutionCatalog` 中 `kind = BROKER` 的条目，`isBrokerName`）即为投资账户，
不另存标记，因此无需迁移，同步与备份也无需新字段。添加账户选「券商」类型时，机构列表只显示券商，
名称必须能识别为券商（可在券商名后加备注，如「富途 美股」）；否则提示并禁止保存，避免账户落入银行分区。
目录覆盖盈透、嘉信、富达、先锋领航、Robinhood、富途、moomoo、老虎、长桥、微牛、雪盈、盈立、华盛通、耀才、
东方财富、华泰、中信证券、招商证券、国泰海通（国泰君安）。

在自己的 IBKR 网页端 Client Portal 中配置；不要把 Token、报表或账号截图发到公开 issue、聊天或代码仓库。

1. 打开 Reports / Performance & Reports → Flex Queries，创建 **Activity Flex Query**。
2. 选择 **XML**，日期格式 **yyyyMMdd**，报表期间 **Last Business Day**。
3. 添加 **Open Positions**，选择 **Summary** 层级，勾选全部字段。不要仅选 Lots。
   必须包含 Account ID、Conid、Symbol、Currency、Quantity、Report Date、Level of Detail；
   建议包含 Description、Asset Class、Model、Position Value、Cost Basis Money、FIFO Unrealized PNL。
4. 添加 **Cash Report**（按币种），勾选 Ending Cash / Ending Settled Cash。
   保证金账户的现金可为负，表示融资欠款；正数是账户里的闲置现金。
5. 保存后记下 **Query ID**。在 Flex Web Service Configuration 中启用服务并生成 **Token**。
6. 回到 Hootbook 填写连接名称、Token 和 Query ID，点击「连接并同步」。

界面内也有配置说明与 IBKR 官方文档链接。此版本严格接受 Open Positions 汇总节；
如果你的空仓报表省略了该节，应用会提示调整查询，而不是把已有持仓错误清零。
Cash Report 可选：没有该节时仍能显示持仓，但不会显示现金或融资欠款。

## 显示和同步语义

- 只读报表：显示标的、数量、原币市值、原币成本和原币浮动盈亏。没有返回的可选金额显示「—」，不伪造为 0。
- 数量与金额使用十进制定点字符串保存，计算使用 BigDecimal，支持碎股、空头和合约。
- 直接使用券商报告的 positionValue，不用数量乘价格重新推算，以免遗漏期权等合约乘数。
- 若查询包含 Cash Report，按币种显示 Ending Cash：正数为现金，负数为融资欠款。快照 = 持仓市值 + 现金（含负数）。
- 持仓与现金按币种分别汇总，**不是完整 NAV**（不含期货盯市等到账项）。目前不做跨币种折算，不自动写入记账余额或交易。
- 投资账户是独立账户类型，持仓快照（含现金和融资欠款）计入首页和资料页净资产。若同一组合既手动记账又连接了快照，两边都会计入。现有账户名称/图标预设不自动连接。
- 一个 Flex 查询可以返回多个账户。账户 ID 限定在连接中，标的 ID 限定在账户中；多日期数据仅保留最新快照。
- 同一账户同一天的重复 Summary 行会报错；Lot 行不与 Summary 行叠加。不同 Model 保留为独立持仓行。
- 仅由用户点击触发联网；启动时只读取本机缓存。报表日期和最近成功同步时间分别展示，不把旧报表标为实时行情。
- 网络、Token、查询格式或磁盘写入失败时保留上一份有效缓存。较旧报表不覆盖同一账户较新的快照。
- 刷新替换当前快照，已清仓标的从列表移除，不追加重复持仓。变更查询所包含的账户会改变此次连接的账户列表。
- 同一提供方的重复凭据或重叠账户被拒绝，避免重复展示。
- Token 过期后，在 IBKR 生成新 Token，在 Hootbook 断开旧连接再添加。断开会删除本机凭据及缓存，不关闭券商账户。
  要撤销服务端 Token，应在 IBKR 重新生成 Token；Hootbook 的本地删除不等于远程撤销。

## 安全与隐私

- 直接通过 HTTPS 请求 `ndcdyn.interactivebrokers.com` 的 SendRequest / GetStatement；没有 Hootbook 中转服务器，也没有交易、下单、转账接口。
- 仅用户主动连接/刷新时向 IBKR 发送其 Flex Token、Query ID（后续请求使用报表引用码）、协议版本和 User-Agent。
- Flex 协议使用 URL 查询参数传递 Token：此专用客户端不启用 HTTP 日志，不保留原始网络异常，不跟随重定向，也不采用服务端返回的 URL。
- 不上传现有记账数据；IBKR 仍会看到连接来源 IP 并按自身政策处理 API 访问。
- 连接、凭据、账户和持仓全部用 AES-GCM 加密，随机 nonce，密钥保存在 Android Keystore。
  原子写入 `noBackupFilesDir/brokerage-v1.enc`，不进入系统迁移/备份。
- Hootbook 备份默认不含券商数据。导出备份时勾选“券商连接（含 Flex token）”，或云备份设置了端到端
  加密密码时，连接、凭据、账户和持仓才写进备份 zip 的 `brokerage.json`（本地导出的文件不加密，界面
  会提示）。导入时只新增连接：同一券商下凭据相同或账户 ID 重叠的跳过；恢复失败不影响记账数据导入。
  设备间同步（sync）从不包含券商数据。
- 加密失效时直接报错，不回退到明文。未从备份恢复时，重新安装或设备迁移后须重新连接。
- Token 表单使用密码输入，禁止该对话框截图；Token 不进入 SavedStateHandle、rememberSaveable、导航参数或 UI 列表状态。
- 报表请求串行并间隔 7 秒，遵守每秒 1 次、每分钟 10 次限制；报表未就绪最多轮询 6 次。
  网络请求有超时，响应最大 5 MiB。XML 拒绝 DTD / ENTITY 声明与外部实体。

## 扩展新券商

`domain/brokerage/BrokerageProvider.kt` 定义与 IBKR 无关的只读 `fetchHoldings(credentials)`、
`BrokerageAccount` 与 `Holding`。`BrokerCredentials.fields` 可携带不同提供方的凭据字段，
其字符串表示不展示字段值。持仓模型中不放原始供应商响应。

新增券商时：

1. 实现独立 `BrokerageProvider`，使用稳定唯一的 provider ID，将供应商数据转换为统一快照；
   `institutionId` 指向 `InstitutionCatalog` 中的条目，列表用它显示名称和图标（缺省为 provider ID）。
2. 在 `BrokerageModule` 通过 Hilt `@IntoSet` 注册。Repository 按 provider ID 路由，负责持久化、去重与状态发布；
   `availableProviders` 列出全部已注册提供方，「连接券商自动同步」列表据此生成，无需改动列表界面。
3. 在 `BrokerConnectForm`（`investments/BrokerAutoSync.kt`）为该 provider ID 添加专用连接表单，
   不要强行复用 IBKR 的认证字段。没有表单的提供方点「连接」时会提示当前版本不支持。
4. 持仓列表、缓存、刷新和断开逻辑可以复用。OAuth 续期、后台定时同步、商业平台服务端密钥及实时行情均需额外实现，当前不假装已支持。

## 验证

```sh
./gradlew :app:testStandardDebugUnitTest --tests 'io.github.hootkit_dev.hootbook.data.brokerage.*' --no-configuration-cache
./gradlew :app:compileStandardDebugAndroidTestKotlin --no-configuration-cache
```

仓库中包含 XML 边界、两阶段 API、延迟轮询、重定向拒绝、凭据隐藏、加密认证、缓存失败保留、多券商路由的单元测试；
界面测试位于 `androidTest/.../investments/InvestmentsScreenTest.kt`，涵盖连接、币种分组、刷新、错误和大字体。
CI 的 Tests 工作流新增 brokerage-tests 作业。测试仅使用明确的合成账户/报表，未读取真实账户。

## 官方参考

- [IBKR Flex 配置](https://www.interactivebrokers.com/docs/web-api/flex-web-service/client-portal-configuration)
- [生成报表与限流](https://www.interactivebrokers.com/docs/web-api/flex-web-service/using-flex-web-service/generate-the-report)
- [获取报表](https://www.interactivebrokers.com/docs/web-api/api-reference/get-statement)
- [Open Positions 字段](https://www.ibkrguides.com/reportingreference/reportguide/open%20positionsfq.htm)
- [Cash Report 字段](https://guides.interactivebrokers.com/reportingreference/reportguide/cash%20reportfq.htm)
- [错误码](https://www.ibkrguides.com/complianceportal/flex3error.htm)
