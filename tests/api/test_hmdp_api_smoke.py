import pytest


@pytest.mark.api
def test_shop_type_list_smoke(api_session, base_url):
    resp = api_session.get(f"{base_url}/shop-type/list", timeout=15)
    assert resp.status_code == 200
    body = resp.json()
    assert body.get("success") is True
    assert isinstance(body.get("data"), list)
    assert body.get("data"), "Expected non-empty shop type list."


@pytest.mark.api
def test_user_me_requires_auth(api_session, base_url):
    resp = api_session.get(f"{base_url}/user/me", timeout=15)
    assert resp.status_code == 401


@pytest.mark.api
def test_user_me_authenticated(api_session, base_url, auth_headers):
    resp = api_session.get(
        f"{base_url}/user/me",
        headers=auth_headers,
        timeout=15,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body.get("success") is True
    assert body.get("data"), "Expected user profile in /user/me response."


@pytest.mark.api
def test_query_voucher_list_for_seckill_shop(api_session, base_url, seckill_voucher):
    resp = api_session.get(f"{base_url}/voucher/list/{seckill_voucher['shop_id']}", timeout=15)
    assert resp.status_code == 200
    body = resp.json()
    assert body.get("success") is True
    assert any(int(v.get("id")) == seckill_voucher["voucher_id"] for v in (body.get("data") or []))


@pytest.mark.api
def test_seckill_same_user_cannot_order_twice(api_session, base_url, seckill_voucher, token_factory):
    issued = token_factory()
    voucher_id = seckill_voucher["voucher_id"]
    headers = {"authorization": issued["token"]}

    first = api_session.post(f"{base_url}/voucher-order/seckill/{voucher_id}", headers=headers, timeout=15)
    assert first.status_code == 200
    first_body = first.json()
    if first_body.get("success") is not True:
        pytest.skip(f"First seckill did not succeed for duplicate check: {first_body}")

    second = api_session.post(f"{base_url}/voucher-order/seckill/{voucher_id}", headers=headers, timeout=15)
    assert second.status_code == 200
    second_body = second.json()
    assert second_body.get("success") is False
    assert "重复" in str(second_body.get("errorMsg") or "")


@pytest.mark.api
def test_logout_revokes_token(api_session, base_url, token_factory):
    issued = token_factory()
    headers = {"authorization": issued["token"]}

    logout_resp = api_session.post(f"{base_url}/user/logout", headers=headers, timeout=15)
    assert logout_resp.status_code == 200
    logout_body = logout_resp.json()
    assert logout_body.get("success") is True

    me_resp = api_session.get(f"{base_url}/user/me", headers=headers, timeout=15)
    assert me_resp.status_code == 401


@pytest.mark.api
def test_seckill_with_invalid_token_rejected(api_session, base_url, seckill_voucher):
    resp = api_session.post(
        f"{base_url}/voucher-order/seckill/{seckill_voucher['voucher_id']}",
        headers={"authorization": "invalid-token"},
        timeout=15,
    )
    assert resp.status_code == 401
