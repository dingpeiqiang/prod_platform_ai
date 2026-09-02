package com.sitech.prodai.service.common;

/**
 * 上游依赖失败导致的步骤中止信号（P3-2 抽公共，原 DefaultExecutor 私有内部类提为 public）。
 * <p>
 * 属内部控制流信号：调用方捕获后应将该步骤记为失败并继续后续无依赖步骤，
 * 而非向用户透出堆栈。
 */
public class DependencyFailedException extends RuntimeException {

    public DependencyFailedException(String message) {
        super(message);
    }
}
