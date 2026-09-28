from typing import Any, Dict

async def main(args):
    p = args.params
    chat_id = str(p.get('chat_id') or '')
    offer_id = str(p.get('offer_id') or '')
    offer_name = str(p.get('offer_name') or '')
    req_id = str(p.get('req_id') or '')
    qs = ('?chatId=' + chat_id + '&req_id=' + req_id + '&stage=01&view=stage')
    if offer_id:
        qs = ('?offer_id=' + offer_id + '&name=' + offer_name + '&chatId=' + chat_id + '&req_id=' + req_id + '&stage=01&view=stage')
    biz_url = 'http://10.86.13.201:31280/ops-web/config-workbench.html' + qs
    panel = (
        "\n\n```xsbot-panel\n"
        + '{"version":"1.0","message_id":"' + chat_id + '",'
        + '"panels":[{"panel":"right","mode":"external","url":"' + biz_url + '","title":"配置方案"}]}'
        + "\n```\n"
    )
    result: Output = {'panel': panel}
    return result

