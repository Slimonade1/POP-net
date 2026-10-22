#!/usr/bin/env bash
# Start MySQL in a container, then run the app. Creates .env on first run and
# offers to install or start whatever is missing.
set -euo pipefail
cd "$(dirname "$0")"

if [[ -t 1 ]]; then
    bold=$'\e[1m' green=$'\e[32m' red=$'\e[31m' reset=$'\e[0m'
else
    bold='' green='' red='' reset=''
fi
step() { echo "${bold}==>${reset} $*"; }
ok()   { echo "  ${green}ok${reset}  $*"; }
fail() {
    echo "  ${red}error${reset}  $1" >&2
    shift
    for hint in "$@"; do echo "         $hint" >&2; done
    exit 1
}

# Ask a yes/no question, defaulting to yes. Without a terminal the answer is no.
confirm() {
    [[ -t 0 ]] || return 1
    local answer
    read -rp "  $1 [Y/n] " answer
    [[ -z $answer || $answer == [Yy]* ]]
}

# Install packages with the system package manager. Arguments are the package
# lists for pacman, apt, dnf and brew, in that order.
install_packages() {
    if command -v pacman >/dev/null; then
        sudo pacman -S --needed --noconfirm $1
    elif command -v apt-get >/dev/null; then
        sudo apt-get update && sudo apt-get install -y $2
    elif command -v dnf >/dev/null; then
        sudo dnf install -y $3
    elif command -v brew >/dev/null; then
        brew install $4
    else
        fail "No supported package manager found." "Install it by hand, then run this again."
    fi
}

step "Checking Java"
java_major=0
if command -v java >/dev/null; then
    java_major=$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')
fi
if (( java_major < 21 )); then
    confirm "The app needs Java 21 or newer. Install it?" \
        || fail "Java 21 or newer is required."
    install_packages jdk21-openjdk openjdk-21-jdk java-21-openjdk-devel 'openjdk@21'
    hash -r
    java_major=$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')
fi
ok "Java $java_major"

step "Checking Docker"

# Find a compose command: the docker plugin, standalone docker-compose, or podman.
find_compose() {
    if docker compose version >/dev/null 2>&1; then
        compose=(docker compose)
    elif command -v docker-compose >/dev/null; then
        compose=(docker-compose)
    elif command -v podman >/dev/null && podman compose version >/dev/null 2>&1; then
        compose=(podman compose)
    else
        return 1
    fi
}

if ! find_compose; then
    if command -v docker >/dev/null; then
        question="Docker Compose is missing. Install it?"
    else
        question="MySQL runs in Docker, which is not installed. Install and set up Docker?"
    fi
    confirm "$question" || fail "Docker with Compose is required."
    install_packages 'docker docker-compose' 'docker.io docker-compose-v2' \
        'moby-engine docker-compose' 'docker docker-compose colima'
    hash -r
    find_compose || fail "Docker Compose is still missing after the install."
fi
ok "${compose[*]}"

if [[ ${compose[0]} != podman ]] && ! docker info >/dev/null 2>&1; then
    if ! command -v systemctl >/dev/null; then
        confirm "Docker is not running. Start it?" || fail "Docker must be running."
        colima start
    elif ! systemctl is-active --quiet docker; then
        confirm "Docker is not running. Start it?" || fail "Docker must be running."
        if ! sudo systemctl start docker; then
            # After a kernel upgrade the running kernel's modules are gone, so
            # Docker cannot load its networking modules until a reboot.
            if [[ ! -d /usr/lib/modules/$(uname -r) ]]; then
                fail "Docker could not start: the kernel was upgraded since the last boot." \
                    "Reboot, then run this again."
            fi
            fail "Docker could not start." "See why with: journalctl -xeu docker.service"
        fi
    fi

    # Running but not allowed: the user is not in the docker group, or was just
    # added and has not logged in again. Use sudo for this run.
    if ! docker info >/dev/null 2>&1; then
        if ! id -nG "$USER" | grep -qw docker \
            && confirm "Let $USER use Docker without sudo from the next login?"; then
            sudo usermod -aG docker "$USER"
        fi
        compose=(sudo "${compose[@]}")
    fi
fi
ok "Docker is running"

step "Loading .env"
if [[ ! -f .env ]]; then
    gen() { head -c 18 /dev/urandom | base64 | tr -d '/+='; }
    sed -e "s/^DB_PASSWORD=.*/DB_PASSWORD=$(gen)/" \
        -e "s/^MYSQL_ROOT_PASSWORD=.*/MYSQL_ROOT_PASSWORD=$(gen)/" \
        .env.example > .env
    ok "Created .env with random passwords"
else
    ok "Using existing .env"
fi
set -a
source .env
set +a

step "Starting MySQL on port 3307 and waiting until it is healthy"
"${compose[@]}" up -d --wait || fail "MySQL did not start." \
    "See its log:  ${compose[*]} logs mysql" \
    "If the password changed since the volume was created, reset it with:" \
    "  ${compose[*]} down --volumes"
ok "MySQL is up"

# Use 8080 unless something else already listens there.
port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
port=${SERVER_PORT:-8080}
if port_busy "$port"; then
    taken=$port
    while port_busy "$port"; do port=$((port + 1)); done
    ok "Port $taken is in use, using $port instead"
fi
export SERVER_PORT=$port

step "Starting the app at ${bold}http://localhost:$port${reset}"
echo "     The first run downloads dependencies, which takes a minute. Stop with Ctrl+C."
echo "     MySQL keeps running afterwards; stop it with: ${compose[*]} down"
exec ./mvnw --no-transfer-progress spring-boot:run
