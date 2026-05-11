# Docker 配置说明

## 问题：OCI Manifest 不兼容

私有仓库 `10.86.12.11:20200` 不支持 OCI 格式的 manifest，需要使用 Docker v2 格式。

## 解决方案 1：修改 Docker Daemon 配置

### Windows (Docker Desktop)

1. 打开 Docker Desktop 设置
2. 进入 Docker Engine 页面
3. 添加以下配置：

```json
{
  "insecure-registries": ["10.86.12.11:20200"],
  "features": {
    "buildkit": false
  }
}
```

4. 点击 "Apply & Restart"

## 解决方案 2：使用构建脚本

脚本已经内置了多个备选方案：
1. 先用 buildx 尝试构建
2. 如果失败，回退到标准构建
3. 如果推送失败，尝试 save/load 再推送
4. 如果全部失败，提供备选方案说明

## 解决方案 3：只导出 TAR 文件（推荐）

如果以上都不行，可以：
1. 在外部网络构建并导出为 TAR 文件
2. 复制 TAR 文件到内网
3. 在内网加载、打标签、推送

这个方案最稳妥！
