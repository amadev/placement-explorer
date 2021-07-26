import os_sdk_light as osl


mapping = {'disk_gb': ['disk', lambda x: x * 1024],
           'memory_mb': ['memory'],
           'vcpu': ['cpu']}

def remap(k, v):
    k = k.lower()
    if k in mapping:
        if len(mapping[k]) == 2:
            v = mapping[k][1](v)
        k = mapping[k][0]
    return [k, v]



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
            k, v = remap(i, inv["inventories"][i]["total"])
            results[p["name"]]["resources"][k] = {
                "total": v
            }
        usages = client.resource_providers.get_usages(uuid=p["uuid"])
        for i in usages["usages"]:
            k, v = remap(i, usages["usages"][i])
            results[p["name"]]["resources"][k]["used"] = v
        allocations = client.resource_providers.get_allocations(uuid=p["uuid"])
        results[p["name"]]["instances"] = {}
        for instance in allocations["allocations"]:
            results[p["name"]]["instances"][instance] = {}
            for k, v in allocations["allocations"][instance]["resources"].items():
                k, v = remap(k, v)
                results[p["name"]]["instances"][instance][k] = v
    return results
