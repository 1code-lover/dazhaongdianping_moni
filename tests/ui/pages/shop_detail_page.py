from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait

from tests.ui.pages.base_page import BasePage


class ShopDetailPage(BasePage):
    SHOP_TITLE = (By.CSS_SELECTOR, ".shop-title")
    SECKILL_BUTTON = (By.CSS_SELECTOR, ".seckill-box .voucher-btn")
    TOAST_TEXT = (By.CSS_SELECTOR, ".el-message__content")

    def open_shop(self, shop_id: int):
        self.open(f"/shop-detail.html?id={shop_id}")

    def set_session_token(self, token: str):
        # Token is read from sessionStorage during page bootstrap in common.js.
        self.open("/index.html")
        self.driver.execute_script("sessionStorage.setItem('token', arguments[0]);", token)

    def click_first_seckill_button(self):
        self.click(self.SECKILL_BUTTON)

    def wait_seckill_button(self, timeout: int = 12) -> bool:
        try:
            WebDriverWait(self.driver, timeout).until(ec.element_to_be_clickable(self.SECKILL_BUTTON))
            return True
        except Exception:
            return False

    def wait_redirect_login(self):
        WebDriverWait(self.driver, 5).until(ec.url_contains("login.html"))

    def wait_toast_text(self) -> str:
        return WebDriverWait(self.driver, 8).until(
            ec.visibility_of_element_located(self.TOAST_TEXT)
        ).text
