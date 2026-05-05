from selenium.webdriver.common.by import By

from tests.ui.pages.base_page import BasePage


class LoginPage(BasePage):
    HEADER_TITLE = (By.XPATH, "//div[contains(@class,'header-title') and contains(., '手机号码快捷登录')]")
    PHONE_INPUT = (By.CSS_SELECTOR, "input[placeholder='请输入手机号']")
    CODE_INPUT = (By.CSS_SELECTOR, "input[placeholder='请输入验证码']")
    AGREEMENT_RADIO = (By.CSS_SELECTOR, "input[type='radio'][value='1']")
    LOGIN_BUTTON = (By.XPATH, "//button[contains(., '登录')]")

    def open_login(self):
        self.open("/login.html")

    def login_with_code(self, phone: str, code: str):
        self.type(self.PHONE_INPUT, phone)
        self.type(self.CODE_INPUT, code)
        self._agree_terms()
        self.click(self.LOGIN_BUTTON)

    def page_title(self) -> str:
        return self.text_of(self.HEADER_TITLE)

    def session_token(self) -> str:
        token = self.driver.execute_script("return sessionStorage.getItem('token');")
        return token or ""

    def _agree_terms(self):
        # Element UI custom styles may hide native radio in headless mode.
        try:
            self.click(self.AGREEMENT_RADIO)
            return
        except Exception:
            pass
        radio = self.driver.find_element(*self.AGREEMENT_RADIO)
        self.driver.execute_script(
            """
            arguments[0].checked = true;
            arguments[0].dispatchEvent(new Event('input', {bubbles: true}));
            arguments[0].dispatchEvent(new Event('change', {bubbles: true}));
            """,
            radio,
        )
