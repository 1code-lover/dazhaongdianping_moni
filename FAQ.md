# 常见问题

> 本文档是 [AGENTS.md](AGENTS.md) FAQ 章节的详细版本。

---

## Q1: 如何快速修复代码风格问题？

```bash
# IntelliJ IDEA 快捷键
# Mac: Cmd + Shift + L (触发代码格式化)
# Windows/Linux: Ctrl + Shift + L

# 或通过菜单：Code → Reformat Code
```

---

## Q2: 如何检查单元测试覆盖率？

```bash
mvn clean test jacoco:report
# 查看 target/site/jacoco/index.html
```

---

## Q3: 如何本地运行完整测试套件？

```bash
# 运行所有测试
mvn clean test

# 运行特定测试类
mvn "-Dtest=VoucherOrderServiceImplTest" test

# 运行特定测试方法
mvn "-Dtest=VoucherOrderServiceImplTest#testSeckillVoucher" test
```

---

## Q4: 代码提交前需要做什么检查？

1. 本地运行所有测试：`mvn clean test`
2. 代码格式化：IDE 中执行格式化
3. 检查拼写和注释
4. 验证 commit message 格式
5. 确保没有遗留的调试代码
