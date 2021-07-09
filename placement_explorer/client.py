import os_sdk_light as osl


def get_resources(cloud):
    client = osl.get_client(
        cloud=cloud, service="placement", schema=osl.schema("placement.yaml")
    )
    providers = client.resource_providers.list_resource_providers()[
        "resource_providers"
    ]
    inventories = {}
    usages = {}
    results = {}
    for p in providers:
        results[p["name"]] = {"uuid": p["uuid"], "resources": {}}
        inv = client.resource_providers.get_inventories(uuid=p["uuid"])
        for i in inv["inventories"]:
            results[p["name"]]["resources"][i] = {
                "total": inv["inventories"][i]["total"]
            }
        usages = client.resource_providers.get_usages(uuid=p["uuid"])
        for i in usages["usages"]:
            results[p["name"]]["resources"][i]["used"] = usages["usages"][i]
    return results
