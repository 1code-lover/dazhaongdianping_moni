import os
import shutil
import time
from datetime import datetime
from itertools import count

import pytest
import requests
import redis

try:
    import allure
except Exception:  # pragma: no cover
    allure = None

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager


def pytest_addoption(parser):
    parser.addoption("--base-url", action="store", default=os.getenv("BASE_URL", "http://127.0.0.1:8081"))
    parser.addoption("--ui-base-url", action="store", default=os.getenv("UI_BASE_URL", "http://127.0.0.1:8080"))
    parser.addoption("--browser", action="store", default=os.getenv("BROWSER", "chrome"))
    parser.addoption("--headless", action="store_true", default=os.getenv("HEADLESS", "1") == "1")
    parser.addoption("--auth-token", action="store", default=os.getenv("AUTH_TOKEN", ""))
    parser.addoption("--redis-host", action="store", default=os.getenv("REDIS_HOST", "127.0.0.1"))
    parser.addoption("--redis-port", action="store", default=os.getenv("REDIS_PORT", "6379"))
    parser.addoption("--redis-password", action="store", default=os.getenv("REDIS_PASSWORD", "123456"))
    parser.addoption("--redis-db", action="store", default=os.getenv("REDIS_DB", "0"))
    parser.addoption("--test-phone", action="store", default=os.getenv("TEST_PHONE", ""))


@pytest.fixture(scope="session")
def base_url(pytestconfig):
    return pytestconfig.getoption("--base-url").rstrip("/")


@pytest.fixture(scope="session")
def ui_base_url(pytestconfig):
    return pytestconfig.getoption("--ui-base-url").rstrip("/")


@pytest.fixture(scope="session")
def api_session():
    s = requests.Session()
    s.headers.update({"Content-Type": "application/json"})
    yield s
    s.close()


@pytest.fixture(scope="session")
def redis_client(pytestconfig):
    host = pytestconfig.getoption("--redis-host").strip()
    port = int(pytestconfig.getoption("--redis-port"))
    password = pytestconfig.getoption("--redis-password")
    db = int(pytestconfig.getoption("--redis-db"))
    client = redis.Redis(
        host=host,
        port=port,
        password=password if password else None,
        db=db,
        decode_responses=True,
        socket_timeout=2,
        socket_connect_timeout=2,
    )
    try:
        client.ping()
    except Exception as exc:
        pytest.skip(f"Redis unavailable for auto-login flow: {exc}")
    return client


@pytest.fixture(scope="session")
def login_phone(pytestconfig):
    configured_phone = pytestconfig.getoption("--test-phone").strip()
    if configured_phone:
        return configured_phone
    # Generate a stable, valid 11-digit mobile number for current run.
    tail = str(int(time.time()))[-9:]
    return "13" + tail


def _read_login_code_from_redis(client, phone: str, max_wait_seconds: int = 3) -> str:
    key = f"login:code:{phone}"
    deadline = time.time() + max_wait_seconds
    while time.time() < deadline:
        code = client.get(key)
        if code:
            return str(code)
        time.sleep(0.2)
    return ""


def _issue_token(api_session, base_url: str, redis_client, phone: str) -> str:
    send_resp = api_session.post(f"{base_url}/user/code", params={"phone": phone}, timeout=15)
    assert send_resp.status_code == 200
    send_body = send_resp.json()
    assert send_body.get("success") is True, send_body.get("errorMsg", "send code failed")

    code = _read_login_code_from_redis(redis_client, phone)
    if not code:
        raise AssertionError("Could not fetch login code from Redis.")

    login_resp = api_session.post(
        f"{base_url}/user/login",
        json={"phone": phone, "code": code},
        timeout=15,
    )
    assert login_resp.status_code == 200
    login_body = login_resp.json()
    assert login_body.get("success") is True, login_body.get("errorMsg", "login failed")
    token = (login_body.get("data") or "").strip()
    assert token, "Empty token returned from /user/login"
    return token


@pytest.fixture(scope="session")
def login_code(api_session, base_url, login_phone, redis_client):
    send_resp = api_session.post(f"{base_url}/user/code", params={"phone": login_phone}, timeout=15)
    assert send_resp.status_code == 200
    send_body = send_resp.json()
    assert send_body.get("success") is True, send_body.get("errorMsg", "send code failed")

    code = _read_login_code_from_redis(redis_client, login_phone)
    if not code:
        pytest.skip("Could not fetch login code from Redis; skip auth-dependent tests.")
    return code


@pytest.fixture(scope="session")
def token_factory(api_session, base_url, redis_client):
    phone_seq = count(1)

    def _factory(phone: str = ""):
        if not phone:
            # Generate unique, valid mobile phone number.
            suffix = str(int(time.time() * 1000) + next(phone_seq))[-9:]
            phone = "13" + suffix
        token = _issue_token(api_session, base_url, redis_client, phone)
        return {"phone": phone, "token": token}

    return _factory


@pytest.fixture(scope="session")
def auth_token(pytestconfig, token_factory, login_phone):
    explicit_token = pytestconfig.getoption("--auth-token").strip() or os.getenv("SECKILL_TOKEN", "").strip()
    if explicit_token:
        return explicit_token
    return token_factory(login_phone)["token"]


@pytest.fixture(scope="session")
def auth_headers(auth_token):
    return {"authorization": auth_token}


def _parse_backend_datetime(raw_value: str):
    if not raw_value:
        return None
    normalized = str(raw_value).replace(" ", "T")
    try:
        return datetime.fromisoformat(normalized)
    except ValueError:
        return None


@pytest.fixture(scope="session")
def seckill_voucher(api_session, base_url):
    for shop_id in range(1, 31):
        resp = api_session.get(f"{base_url}/voucher/list/{shop_id}", timeout=15)
        if resp.status_code != 200:
            continue
        body = resp.json()
        if body.get("success") is not True:
            continue
        vouchers = body.get("data") or []
        for voucher in vouchers:
            if voucher.get("type") and int(voucher.get("stock") or 0) > 0:
                return {
                    "shop_id": shop_id,
                    "voucher_id": int(voucher["id"]),
                    "title": voucher.get("title", ""),
                    "stock": int(voucher.get("stock") or 0),
                    "begin_time": voucher.get("beginTime"),
                    "end_time": voucher.get("endTime"),
                }
    pytest.skip("No seckill voucher with stock found in scanned shops [1..30].")


@pytest.fixture(scope="session")
def active_seckill_voucher(seckill_voucher):
    begin_time = _parse_backend_datetime(seckill_voucher.get("begin_time"))
    end_time = _parse_backend_datetime(seckill_voucher.get("end_time"))
    now = datetime.now()
    if begin_time and now < begin_time:
        pytest.skip("Found seckill voucher but activity has not started yet.")
    if end_time and now > end_time:
        pytest.skip("Found seckill voucher but activity already ended.")
    return seckill_voucher


@pytest.fixture(scope="function")
def driver(pytestconfig):
    browser = pytestconfig.getoption("--browser").lower().strip()
    if browser != "chrome":
        pytest.skip(f"Unsupported browser in this template: {browser}")

    options = Options()
    if pytestconfig.getoption("--headless"):
        options.add_argument("--headless=new")
    options.add_argument("--window-size=1440,900")
    options.add_argument("--disable-gpu")
    options.add_argument("--no-sandbox")

    web_driver = None
    init_errors = []
    try:
        service = Service(ChromeDriverManager().install())
        web_driver = webdriver.Chrome(service=service, options=options)
    except Exception as exc:
        init_errors.append(f"webdriver-manager init failed: {exc}")

    if web_driver is None:
        local_driver = shutil.which("chromedriver")
        if local_driver:
            try:
                web_driver = webdriver.Chrome(service=Service(local_driver), options=options)
            except Exception as exc:
                init_errors.append(f"local chromedriver init failed: {exc}")

    if web_driver is None:
        try:
            # Final fallback: rely on Selenium default discovery.
            web_driver = webdriver.Chrome(options=options)
        except Exception as exc:
            init_errors.append(f"selenium default init failed: {exc}")

    if web_driver is None:
        pytest.skip("Chrome driver init failed; " + " | ".join(init_errors))

    yield web_driver
    web_driver.quit()


@pytest.fixture(autouse=True)
def attach_failure_artifacts(request):
    if "driver" not in request.fixturenames:
        yield
        return

    driver = request.getfixturevalue("driver")
    yield
    if request.node.rep_call.failed and allure is not None:
        try:
            png = driver.get_screenshot_as_png()
            allure.attach(
                png,
                name=f"screenshot-{datetime.now().strftime('%Y%m%d-%H%M%S')}",
                attachment_type=allure.attachment_type.PNG,
            )
            allure.attach(
                driver.page_source,
                name="page-source",
                attachment_type=allure.attachment_type.HTML,
            )
        except Exception:
            # Keep reporting best-effort; never hide real assertion failure.
            pass


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    setattr(item, "rep_" + rep.when, rep)
