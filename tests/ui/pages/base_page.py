from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait


class BasePage:
    def __init__(self, driver, base_url: str):
        self.driver = driver
        self.base_url = base_url.rstrip("/")
        self.wait = WebDriverWait(driver, 10)

    def open(self, path: str = ""):
        url = f"{self.base_url}/{path.lstrip('/')}" if path else self.base_url
        self.driver.get(url)

    def click(self, locator):
        self.wait.until(ec.element_to_be_clickable(locator)).click()

    def type(self, locator, text: str):
        element = self.wait.until(ec.visibility_of_element_located(locator))
        element.clear()
        element.send_keys(text)

    def text_of(self, locator) -> str:
        return self.wait.until(ec.visibility_of_element_located(locator)).text
