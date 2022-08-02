import { Body, Controller, Get, Post, Put, Req, UseFilters, Param, HttpCode, ParseIntPipe, Query, UsePipes, ValidationPipe } from '@nestjs/common';
import { ErrorFilter } from '../../../libs/filters/error.filter';
import { Request } from 'express';
import { HoneycombService } from '../../../libs/honeycomb/honeycomb.service';
import { AuthService } from '../../../services/auth/auth.service';
import { AdminPermission } from '../../../enums/admin-permission';
import { RealIP } from 'nestjs-real-ip';
import { APIError } from '../../../libs/errors/api-error';
import { IsEmail, IsString, IsOptional } from 'class-validator';
import { camelize } from '@angular-devkit/core/src/utils/strings';

@UsePipes(new ValidationPipe({
    transform: true
}))
@UseFilters(ErrorFilter)
@Controller()
export class HealthController {
    constructor(
        private honeycombService: HoneycombService,
        private authService: AuthService,
    ) {}

    @Get('/health')
    async health(
        @Req() request: Request
    ): Promise<any> {
        return this.honeycombService.start(request, {

        }, async () => {
            this.honeycombService.log('test');
            return "Healthy";
        })
    }


    @Get('/api/health')
    async healthAPI(
        @Req() request: Request
    ): Promise<any> {
        return {
            status: "Healthy"
        }
    }

    @Post('/debug/error')
    async testError(
        @Req() request: Request
    ): Promise<any> {
        throw new APIError(500, { message: "Some error"});
        return {}
    }
}
