import base64
import json
import hashlib
import random

# User's RSC object 1:
obj1 = {
    "bskvazb":"98ec38903b1c737c13e9e22fa8350fe25ad1a62c59f1bdc2bfef9ad50d3a0f3c8464362553a78815868b20f4165b7e104582a426474f60f4dbb2d4892c9921d13cedb7bc9e6b7e054d9e6ea332e99ad01fad75fdeca8a00591ac3db4bef3313f69766f04b43e552b5aafd15e",
    "pfamxapwxsb":"24683588fea881060e2bf2c32aedb9b4d5fd4bbc8a72eceef01e57f63b676fa68d10f6908c1171a875e0672ca361402cb6913ea3d276e7b5ca6e52998c34fc55f7e057314d90dfc12338aef12607f4d7a89a1323fdc15ee15dad46899946d2a21c65858e04897493cf33543f",
    "biuqbogg":"2d7b20ae89fb41917301e2e14ca0fc2d18fb354345b50f858b6e2a36d9684938df8b8b1ae1cd9ddf81093023a90fe5b4cf6c935c66ea71176248825196f15c6704d371f7b55fb4870947d2fa115270a8a2939c44438298520a4b852fcc49028cdf6b66351a4e5265093d6a",
    "iybyjq":"faaf26de5b4dd93f22f92426bb3a75840abc2e937550238e1320acc848a724a9369b949f489598c4bf8673351ad1ffc794c4412499b1a1f44235e5b98674d01d822de8c35f813f5bf5a5c0c7c1797d74c6630f26f64efdd5b4d495b79217619ec4b1ece62f771c89e428aca3e2",
    "hahdb":"f9a4b94766f396ed8918740c0265d049601c42c2b2867052511fc5d9dc5985c07463fe2696ad31fbe9dac3401ee63eb4568621f17e9229a55869f654452ca4e6d791a72c0368cd6e7a7fb66a106327416a178ffdf8c61650aaed86f6cdaa14717545ac9a61d7427c1a30e4b",
    "czfgfis":"==gJsJooAruHs0XkiOo6Z81bfyLy5HVCgc8+QWrPLUy2+b8SqVAKYP6xNJWU74t+vikMfYnhyf+T0AFMHG8q"
}

# User's RSC object 2:
obj2 = {
    "capaistpot":"0c6bae98a094bc7849c3f342688859bc4a9699f4b05be4e875e276c055a8856a852dd6cfbc83d18e8a31a90352aa9328a64bccb9e291e78e0658156b06724ecc5ebe2d1cdbcc5f0a",
    "hhdizuxhk":"a69c0567024bfa3caa5619e40a984676a630944f1aa6822da72f3dd4eb25377733b199e3f27a4279e763351dd63592b60e10cdfe133663511382c834fe586b7653cd5ea53a939ea1",
    "jyopa":"44754db3408487cf0e23cd693b343065095ded81bf5bffe1364f5189d85fa2e4104dbbc7710f55117cdf68391326f3e56eabea1132597400e34bc938fc6bd6f7a2879dbfe8449b84",
    "jevptbxmew":"45a28a41e308bc9ded565b840c733c42eed9c59307430a62a22f7f74a8f01c6f09c6526a4c731a813e55e0bb50ebbff35a3c4eeacc4edb543f8c4e164d7466b98bd82025e716e6",
    "obuggo":"5932fa88f29e6607cad4b3aee9fde3d34c10cb792b31ca94a7e28724089a97efd45f5b2c8aaf5c503490191a793231ead92ee215359a534eb0a4bb02e4931721522203b6343b07d0e4d",
    "nyutdhd":"aa8e226c7308302c502e3c617e391e91e2b6c4475c011a32332a9fb3364bdc129f2400c399569ca258dffeb2ac7928aaca04019a732756c8551c096f2357d361f3d66de9815dff753",
    "jfwlay":"87d3edd225641aac0cd7954f2b47e8ea14e356e37da6b7edd259f6e4f7f5d72be9b2929a6680074aa481d75915357df0e843a0f5d0476b54483c0bac601938c0663675146b16ffe0aa83",
    "wjqrsboynmp":"=cUcz6M+DNySGyqzm7QLIVtoez/ExQXmj+s6YklIET+nzWVClcp+YWLPI0C27TZsvpUJOu7lbQSBsV59"
}

def is_seed_map(m):
    for k, v in m.items():
        rev = v[::-1]
        padded = rev + '=' * ((4 - len(rev) % 4) % 4)
        try:
            dec = base64.b64decode(padded).decode('latin1', errors='ignore')
            if '.' in dec:
                return True
        except Exception:
            pass
    return False

def find_seed_entry(m):
    for k, v in m.items():
        rev = v[::-1]
        padded = rev + '=' * ((4 - len(rev) % 4) % 4)
        try:
            dec = base64.b64decode(padded).decode('latin1', errors='ignore')
            if '.' in dec:
                return k, rev
        except Exception:
            pass
    return None

print("obj1 is seed map?", is_seed_map(obj1))
print("obj1 seed entry:", find_seed_entry(obj1))
print("obj2 is seed map?", is_seed_map(obj2))
print("obj2 seed entry:", find_seed_entry(obj2))
