import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

import * as jwt from 'jsonwebtoken';

import { createRemoteJWKSet, jwtVerify } from 'jose';
import { EnvironmentVariable } from '../../constants';


@Injectable()
export class JwtService {
    constructor(private configService: ConfigService) {}

    extractJWTCookie(key: string, token: string): Promise<number | null> {
        const promise = new Promise<number | null>((resolve, reject) => {
            jwt.verify(token, this.configService.get(EnvironmentVariable.JWT_SECRET) || "", {
                algorithms: ['HS256']
            }, function(err, decoded) {
                if(err) {
                    return resolve(null);
                } else {
                    const parsedJSON = decoded as any;
                    if(parsedJSON[key]) {
                        const parsedInt = parseInt(parsedJSON[key])
                        if(parsedInt) {
                            return resolve(parsedInt)
                        } else {
                            return resolve(null)
                        }
                    } else {
                        return resolve(null)
                    }
                }
            });
        });
        
        return promise;
    }

    async extractAuth0Cookie(token: string, domain: string): Promise<number | null> {
        const jwks = createRemoteJWKSet(new URL(`https://${domain}/.well-known/jwks.json`));
        try {
            const { payload, protectedHeader } = await jwtVerify(token, jwks); // The jwt variable needs to be passed in from somewhere; cookie, hard coded, parameter, etc.

            const aud = payload.aud;
            const iss = payload.iss;
            if (iss === `https://${domain}/`) {
                return payload['https://givinga-additional-data.com/userId'] as number | null;
            } else {
                return null;
            }
        } catch (e) {
            return null;
        }
    }

    mintJWTToken(key: string, id: number, expiresIn: number) {
        return jwt.sign(
            { [key]: id },
            this.configService.get(EnvironmentVariable.JWT_SECRET) || "",
            { algorithm: 'HS256', expiresIn }
        );
    }
}
