#!/bin/bash

# Knight Server Java 启动脚本
# 功能说明：一键启动游戏服务器集群，支持多种部署方式
# 技术选型：Bash脚本 + Docker Compose + Maven
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
LOG_DIR="$PROJECT_ROOT/logs"
PID_DIR="$PROJECT_ROOT/pids"

# 创建必要目录
mkdir -p "$LOG_DIR" "$PID_DIR"

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

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
Knight Server Java 启动脚本

用法: $0 [选项] [服务名...]

选项:
    -h, --help          显示帮助信息
    -m, --mode          启动模式: local|docker|k8s (默认: local)
    -p, --profile       环境配置: dev|test|prod (默认: dev)
    -c, --check         启动前检查环境依赖
    -b, --build         启动前重新构建
    -d, --daemon        后台运行模式
    -v, --verbose       详细日志模式

服务名:
    all                 启动所有服务
    gateway             网关服务
    logic               逻辑服务
    chat                聊天服务
    payment             支付服务
    admin               管理后台

示例:
    $0 all                          # 启动所有服务（本地模式）
    $0 -m docker all                # Docker模式启动所有服务
    $0 -p prod gateway logic        # 生产环境启动网关和逻辑服务
    $0 -b -c gateway                # 重新构建并检查依赖后启动网关服务

EOF
}

# 检查环境依赖
check_dependencies() {
    log_info "检查环境依赖..."
    
    # 检查Java版本
    if ! command -v java &> /dev/null; then
        log_error "Java未安装或不在PATH中"
        exit 1
    fi
    
    local java_version=$(java -version 2>&1 | grep "openjdk version" | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt "17" ]; then
        log_error "需要Java 17或更高版本，当前版本: $java_version"
        exit 1
    fi
    log_info "Java版本检查通过: $java_version"
    
    # 检查Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven未安装或不在PATH中"
        exit 1
    fi
    log_info "Maven检查通过"
    
    # 根据启动模式检查对应依赖
    case "$MODE" in
        "docker")
            if ! command -v docker &> /dev/null; then
                log_error "Docker未安装或不在PATH中"
                exit 1
            fi
            if ! command -v docker-compose &> /dev/null; then
                log_error "Docker Compose未安装或不在PATH中"
                exit 1
            fi
            log_info "Docker环境检查通过"
            ;;
        "k8s")
            if ! command -v kubectl &> /dev/null; then
                log_error "kubectl未安装或不在PATH中"
                exit 1
            fi
            log_info "Kubernetes环境检查通过"
            ;;
    esac
    
    log_info "环境依赖检查完成"
}

# 构建项目
build_project() {
    log_info "开始构建项目..."
    
    cd "$PROJECT_ROOT"
    
    if [ "$VERBOSE" = true ]; then
        mvn clean package -DskipTests
    else
        mvn clean package -DskipTests -q
    fi
    
    if [ $? -eq 0 ]; then
        log_info "项目构建成功"
    else
        log_error "项目构建失败"
        exit 1
    fi
}

# 启动本地服务
start_local_service() {
    local service=$1
    log_info "启动本地服务: $service"
    
    case "$service" in
        "gateway")
            start_local_gateway
            ;;
        "logic")
            start_local_logic
            ;;
        "chat")
            start_local_chat
            ;;
        "payment")
            start_local_payment
            ;;
        "admin")
            start_local_admin
            ;;
        *)
            log_error "未知服务: $service"
            exit 1
            ;;
    esac
}

# 启动本地网关服务
start_local_gateway() {
    local jar_file="$PROJECT_ROOT/game-gateway/target/game-gateway-1.0.0-SNAPSHOT.jar"
    local pid_file="$PID_DIR/gateway.pid"
    local log_file="$LOG_DIR/gateway.log"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p "$pid" > /dev/null 2>&1; then
            log_warn "网关服务已在运行 (PID: $pid)"
            return
        fi
    fi
    
    local java_opts="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
    local spring_opts="--spring.profiles.active=$PROFILE"
    
    if [ "$DAEMON" = true ]; then
        nohup java $java_opts -jar "$jar_file" $spring_opts > "$log_file" 2>&1 &
        local pid=$!
        echo $pid > "$pid_file"
        log_info "网关服务已在后台启动 (PID: $pid)"
    else
        java $java_opts -jar "$jar_file" $spring_opts
    fi
}

# 启动本地逻辑服务
start_local_logic() {
    local jar_file="$PROJECT_ROOT/game-logic/target/game-logic-1.0.0-SNAPSHOT.jar"
    local pid_file="$PID_DIR/logic.pid"
    local log_file="$LOG_DIR/logic.log"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p "$pid" > /dev/null 2>&1; then
            log_warn "逻辑服务已在运行 (PID: $pid)"
            return
        fi
    fi
    
    local java_opts="-Xms4g -Xmx8g -XX:+UseG1GC -XX:+UseStringDeduplication"
    local spring_opts="--spring.profiles.active=$PROFILE"
    
    if [ "$DAEMON" = true ]; then
        nohup java $java_opts -jar "$jar_file" $spring_opts > "$log_file" 2>&1 &
        local pid=$!
        echo $pid > "$pid_file"
        log_info "逻辑服务已在后台启动 (PID: $pid)"
    else
        java $java_opts -jar "$jar_file" $spring_opts
    fi
}

# 启动其他服务的占位函数
start_local_chat() {
    log_warn "聊天服务启动功能开发中..."
}

start_local_payment() {
    log_warn "支付服务启动功能开发中..."
}

start_local_admin() {
    log_warn "管理后台启动功能开发中..."
}

# Docker模式启动
start_docker() {
    log_info "使用Docker Compose启动服务..."
    
    cd "$PROJECT_ROOT"
    
    # 构建镜像
    if [ "$BUILD" = true ]; then
        log_info "构建Docker镜像..."
        docker-compose build
    fi
    
    # 启动服务
    if [ "$DAEMON" = true ]; then
        docker-compose up -d "${SERVICES[@]}"
    else
        docker-compose up "${SERVICES[@]}"
    fi
}

# Kubernetes模式启动
start_k8s() {
    log_info "使用Kubernetes启动服务..."
    
    cd "$PROJECT_ROOT/k8s"
    
    # 应用配置
    kubectl apply -f namespace.yaml
    
    for service in "${SERVICES[@]}"; do
        kubectl apply -f "${service}-deployment.yaml"
    done
    
    log_info "Kubernetes服务部署完成"
}

# 主函数
main() {
    # 默认参数
    MODE="local"
    PROFILE="dev"
    CHECK_DEPS=false
    BUILD=false
    DAEMON=false
    VERBOSE=false
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
            -p|--profile)
                PROFILE="$2"
                shift 2
                ;;
            -c|--check)
                CHECK_DEPS=true
                shift
                ;;
            -b|--build)
                BUILD=true
                shift
                ;;
            -d|--daemon)
                DAEMON=true
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            all)
                SERVICES=("gateway" "logic" "chat" "payment" "admin")
                shift
                ;;
            gateway|logic|chat|payment|admin)
                SERVICES+=("$1")
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 默认启动网关服务
    if [ ${#SERVICES[@]} -eq 0 ]; then
        SERVICES=("gateway")
    fi
    
    log_info "启动Knight Server Java"
    log_info "模式: $MODE, 环境: $PROFILE, 服务: ${SERVICES[*]}"
    
    # 检查依赖
    if [ "$CHECK_DEPS" = true ]; then
        check_dependencies
    fi
    
    # 构建项目
    if [ "$BUILD" = true ]; then
        build_project
    fi
    
    # 根据模式启动服务
    case "$MODE" in
        "local")
            for service in "${SERVICES[@]}"; do
                start_local_service "$service"
            done
            ;;
        "docker")
            start_docker
            ;;
        "k8s")
            start_k8s
            ;;
        *)
            log_error "不支持的启动模式: $MODE"
            exit 1
            ;;
    esac
    
    log_info "服务启动完成"
}

# 执行主函数
main "$@"