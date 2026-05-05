import pytest
import os

from tests.ui.pages.login_page import LoginPage


@pytest.mark.ui
def test_login_page_smoke(driver, ui_base_url):
    if not ui_base_url:
        pytest.skip("UI_BASE_URL not configured; skip Selenium UI test.")

    page = LoginPage(driver, ui_base_url)
    page.open_login()

    assert "手机号码快捷登录" in page.page_title()
    assert "login.html" in driver.current_url.lower()


@pytest.mark.ui
def test_login_action_template(driver, ui_base_url, login_phone, login_code):
    if not ui_base_url:
        pytest.skip("UI_BASE_URL not configured; skip Selenium UI test.")
    if os.getenv("RUN_UI_LOGIN_E2E", "0") != "1":
        pytest.skip("Set RUN_UI_LOGIN_E2E=1 to enable real login action test.")

    page = LoginPage(driver, ui_base_url)
    page.open_login()
    page.login_with_code(login_phone, login_code)

    assert page.session_token(), "Expected session token after login but got empty."
