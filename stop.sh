#!/bin/bash

# Knight Server Java 停止脚本
# 功能说明：优雅停止游戏服务器集群
# 技术选型：Bash脚本 + 信号处理
# @author lx

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
PID_DIR="$PROJECT_ROOT/pids"

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
Knight Server Java 停止脚本

用法: $0 [选项] [服务名...]

选项:
    -h, --help          显示帮助信息
    -m, --mode          停止模式: local|docker|k8s (默认: local)
    -f, --force         强制停止（使用SIGKILL）
    -w, --wait          等待时间（秒），默认30秒

服务名:
    all                 停止所有服务
    gateway             网关服务
    logic               逻辑服务
    chat                聊天服务
    payment             支付服务
    admin               管理后台

示例:
    $0 all                          # 停止所有本地服务
    $0 -m docker all                # 停止Docker容器
    $0 -f gateway                   # 强制停止网关服务
    $0 -w 60 logic                  # 等待60秒后停止逻辑服务

EOF
}

# 优雅停止进程
graceful_stop() {
    local pid=$1
    local service_name=$2
    local wait_time=${3:-30}
    
    if ! ps -p "$pid" > /dev/null 2>&1; then
        log_warn "$service_name 进程不存在 (PID: $pid)"
        return 0
    fi
    
    log_info "发送SIGTERM信号给 $service_name (PID: $pid)"
    kill -TERM "$pid"
    
    # 等待进程正常退出
    local count=0
    while [ $count -lt $wait_time ]; do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            log_info "$service_name 已正常停止"
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done
    
    # 如果进程仍在运行，发送SIGKILL
    if ps -p "$pid" > /dev/null 2>&1; then
        log_warn "$service_name 未在指定时间内停止，发送SIGKILL信号"
        kill -KILL "$pid"
        sleep 2
        
        if ps -p "$pid" > /dev/null 2>&1; then
            log_error "无法停止 $service_name (PID: $pid)"
            return 1
        else
            log_info "$service_name 已强制停止"
            return 0
        fi
    fi
}

# 强制停止进程
force_stop() {
    local pid=$1
    local service_name=$2
    
    if ! ps -p "$pid" > /dev/null 2>&1; then
        log_warn "$service_name 进程不存在 (PID: $pid)"
        return 0
    fi
    
    log_info "强制停止 $service_name (PID: $pid)"
    kill -KILL "$pid"
    sleep 2
    
    if ps -p "$pid" > /dev/null 2>&1; then
        log_error "无法停止 $service_name (PID: $pid)"
        return 1
    else
        log_info "$service_name 已强制停止"
        return 0
    fi
}

# 停止本地服务
stop_local_service() {
    local service=$1
    local pid_file="$PID_DIR/${service}.pid"
    
    if [ ! -f "$pid_file" ]; then
        log_warn "$service PID文件不存在: $pid_file"
        return 0
    fi
    
    local pid=$(cat "$pid_file")
    
    if [ "$FORCE" = true ]; then
        force_stop "$pid" "$service"
    else
        graceful_stop "$pid" "$service" "$WAIT_TIME"
    fi
    
    # 删除PID文件
    rm -f "$pid_file"
}

# 停止所有本地服务
stop_all_local() {
    log_info "停止所有本地服务..."
    
    local services=("gateway" "logic" "chat" "payment" "admin")
    
    for service in "${services[@]}"; do
        if [[ "${SERVICES[@]}" =~ "$service" ]] || [[ "${SERVICES[@]}" =~ "all" ]]; then
            stop_local_service "$service"
        fi
    done
}

# 停止Docker服务
stop_docker() {
    log_info "停止Docker服务..."
    
    cd "$PROJECT_ROOT"
    
    if [ "${SERVICES[*]}" = "all" ] || [ ${#SERVICES[@]} -eq 0 ]; then
        docker-compose down
    else
        docker-compose stop "${SERVICES[@]}"
    fi
    
    log_info "Docker服务已停止"
}

# 停止Kubernetes服务
stop_k8s() {
    log_info "停止Kubernetes服务..."
    
    cd "$PROJECT_ROOT/k8s"
    
    for service in "${SERVICES[@]}"; do
        if [ "$service" = "all" ]; then
            kubectl delete -f .
            break
        else
            kubectl delete -f "${service}-deployment.yaml" || true
        fi
    done
    
    log_info "Kubernetes服务已停止"
}

# 检查服务状态
check_status() {
    log_info "检查服务状态..."
    
    case "$MODE" in
        "local")
            local services=("gateway" "logic" "chat" "payment" "admin")
            for service in "${services[@]}"; do
                local pid_file="$PID_DIR/${service}.pid"
                if [ -f "$pid_file" ]; then
                    local pid=$(cat "$pid_file")
                    if ps -p "$pid" > /dev/null 2>&1; then
                        log_info "$service 正在运行 (PID: $pid)"
                    else
                        log_warn "$service PID文件存在但进程不在运行"
                        rm -f "$pid_file"
                    fi
                else
                    log_info "$service 未运行"
                fi
            done
            ;;
        "docker")
            docker-compose ps
            ;;
        "k8s")
            kubectl get pods -n knight-server
            ;;
    esac
}

# 清理资源
cleanup() {
    log_info "清理资源..."
    
    # 清理PID文件
    if [ "$MODE" = "local" ]; then
        find "$PID_DIR" -name "*.pid" -type f | while read -r pid_file; do
            local pid=$(cat "$pid_file" 2>/dev/null || echo "")
            if [ -n "$pid" ] && ! ps -p "$pid" > /dev/null 2>&1; then
                log_info "清理无效PID文件: $pid_file"
                rm -f "$pid_file"
            fi
        done
    fi
    
    log_info "资源清理完成"
}

# 主函数
main() {
    # 默认参数
    MODE="local"
    FORCE=false
    WAIT_TIME=30
    SERVICES=()
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -m|--mode)
                MODE="$2"
                shift 2
                ;;
            -f|--force)
                FORCE=true
                shift
                ;;
            -w|--wait)
                WAIT_TIME="$2"
                shift 2
                ;;
            all)
                SERVICES=("all")
                shift
                ;;
            gateway|logic|chat|payment|admin)
                SERVICES+=("$1")
                shift
                ;;
            status)
                check_status
                exit 0
                ;;
            cleanup)
                cleanup
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 默认停止所有服务
    if [ ${#SERVICES[@]} -eq 0 ]; then
        SERVICES=("all")
    fi
    
    log_info "停止Knight Server Java"
    log_info "模式: $MODE, 服务: ${SERVICES[*]}, 强制: $FORCE, 等待: ${WAIT_TIME}秒"
    
    # 根据模式停止服务
    case "$MODE" in
        "local")
            stop_all_local
            ;;
        "docker")
            stop_docker
            ;;
        "k8s")
            stop_k8s
            ;;
        *)
            log_error "不支持的停止模式: $MODE"
            exit 1
            ;;
    esac
    
    # 清理资源
    cleanup
    
    log_info "服务停止完成"
}

# 执行主函数
main "$@"