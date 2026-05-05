import pytest

from tests.ui.pages.shop_detail_page import ShopDetailPage


@pytest.mark.ui
def test_seckill_click_redirects_to_login_when_not_authenticated(driver, ui_base_url, active_seckill_voucher):
    if not ui_base_url:
        pytest.skip("UI_BASE_URL not configured; skip Selenium UI test.")

    page = ShopDetailPage(driver, ui_base_url)
    page.open_shop(active_seckill_voucher["shop_id"])
    if not page.wait_seckill_button():
        pytest.skip("Seckill button not ready on shop detail page; check nginx upstream/stock data.")
    page.click_first_seckill_button()
    page.wait_redirect_login()

    assert "login.html" in driver.current_url


@pytest.mark.ui
def test_seckill_click_with_token_shows_business_feedback(
    driver, ui_base_url, active_seckill_voucher, auth_token
):
    if not ui_base_url:
        pytest.skip("UI_BASE_URL not configured; skip Selenium UI test.")

    page = ShopDetailPage(driver, ui_base_url)
    page.set_session_token(auth_token)
    page.open_shop(active_seckill_voucher["shop_id"])
    if not page.wait_seckill_button():
        pytest.skip("Seckill button not ready on shop detail page; check nginx upstream/stock data.")
    page.click_first_seckill_button()

    toast = page.wait_toast_text()
    expected_keywords = ("抢购成功", "不能重复下单", "库存不足")
    assert any(keyword in toast for keyword in expected_keywords), toast
