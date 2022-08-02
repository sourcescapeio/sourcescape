import { Body, Controller, Get, Post, Put, Req, UseFilters, Param, HttpCode, ParseIntPipe, Query, UsePipes, ValidationPipe, Delete } from '@nestjs/common';
import { ErrorFilter } from '../../../libs/filters/error.filter';
import { Request } from 'express';
import { HoneycombKeys, HoneycombService } from '../../../libs/honeycomb/honeycomb.service';
import { AuthService } from '../../../services/auth/auth.service';
import { AdminPermission } from '../../../enums/admin-permission';
import { RealIP } from 'nestjs-real-ip';
import { APIError } from '../../../libs/errors/api-error';
import { IsEmail, IsString, IsOptional, IsObject } from 'class-validator';
import { basicSearchFilterWrites, calculateDynamicQuery, FilterGroupStructure, FilterUnitStructure, SearchFilterDao, searchFilterDTOWrites } from '../../../dao/search-filter/search-filter.dao';
import { PrismaService } from '../../../libs/prisma/prisma.service';
import { CharitySearchService } from '../../../services/charity-search/charity-search.service';
import { FilterOperator } from '../../../enums/filter-operator';
import { PrismaClient } from '@prisma/client';

class FilterForm {
    @IsString()
    @IsOptional()
    displayName: string | null;

    @IsString()
    @IsOptional()
    description: string | null;

    @IsOptional()
    query: any | null;
}

class SearchFilterRequest {
    @IsString()
    firstName: string;
  
    @IsString()
    lastName: string;
  
    @IsEmail()
    email: string;
  
    @IsString()
    remoteId: string;
    
    @IsOptional()
    @IsString()
    countryCode: string | null;
  
    @IsOptional()
    @IsString()    
    preferredCurrency: string | null;
}

@UsePipes(new ValidationPipe({
    transform: true
}))
@UseFilters(ErrorFilter)
@Controller()
export class SearchFilterController {
    constructor(
        private honeycombService: HoneycombService,
        private authService: AuthService,
        private searchFilterDAO: SearchFilterDao,
        private prisma: PrismaService,
        private charitySearchService: CharitySearchService,
    ) {}

    @Get('/api/internal/filters')
    async listFilters(
        @Req() request: Request,
    ) {
        return this.honeycombService.start(request, {}, async () => {
            return this.authService.authorize(AdminPermission.APIRead, request, async (req) => {
                const filters = await this.searchFilterDAO.listFilters(this.prisma, req.corporationId);
                const work = filters.map(async (f) => {
                    const grouped = await this.searchFilterDAO.getFullFilter(this.prisma, f.id)
                    try {
                        const { count } = await this.charitySearchService.search(
                            calculateDynamicQuery(grouped),
                            [],
                            0,
                            0,
                            null,
                            false
                        )

                        return {
                            f,
                            count
                        }
                    }
                    catch(e) {
                        return null;
                    }
                })

                const results = await Promise.all(work);

                // Filter out failed search requests
                const filteredResults = results.filter(Boolean) as ({ f: any, count: number})[];

                return {
                    count: filteredResults.length,
                    filters: filteredResults.map(({f, count}) => {
                        return {
                            id: f.id,
                            name: f.name,
                            slug: f.name,
                            displayName: f.display_name,
                            description: f.description,
                            count,
                        }
                    })
                }
            });
        });
    }

    @Post('/api/internal/filters/:name')
    @HttpCode(200)
    async createFilter(
        @Req() request: Request,
        @Param('name') name: string,
        @Body() form: FilterForm,
    ) {
        return this.honeycombService.start(request, {}, async () => {
            this.honeycombService.log('filters.payload', {
                [HoneycombKeys.PAYLOAD]: JSON.stringify(request.body).slice(0, 10000),
                'content-type': request.headers['content-type'] || '',
                'content-length': request.headers['content-length'] || ''
            });
            return this.authService.authorize(AdminPermission.APIWrite, request, async (req) => {
                const parsed = this.charitySearchService.parse(form.query || {});

                const rawFilter = parsed.toSearchFilter();
                let convertedFilter: FilterGroupStructure;
                if (rawFilter instanceof FilterGroupStructure) {
                    convertedFilter = rawFilter;
                } else if (rawFilter instanceof FilterUnitStructure) {
                    convertedFilter = new FilterGroupStructure({
                        id: 0,
                        operator: FilterOperator.AND,
                        groups: [],
                        units: [rawFilter]
                    })
                } else {                    
                    throw new APIError(500, { message: 'should never get here'});
                }
                try {
                    return this.prisma.$transaction(async (prisma: PrismaClient) => {
                        const filter = await this.searchFilterDAO.createFilter(prisma, req.corporationId, name, form.displayName, form.description);
                        await this.searchFilterDAO.deleteGroupsForFilter(prisma, filter.id)
                        await this.searchFilterDAO.createTopLevelGroup(prisma, filter.id, convertedFilter);
                        const compiled = await this.searchFilterDAO.getFullFilter(prisma, filter.id);

                        return {
                            id: filter.id,
                            slug: filter.name,
                            displayName: filter.display_name,
                            description: filter.description,
                            compiled: basicSearchFilterWrites(compiled),
                            query: searchFilterDTOWrites(compiled)
                        };
                    });
                } catch(e) {
                    throw new APIError(500, {message: 'Create filter operation failed'});
                }
            });
        });
    }

    @Get('/api/internal/filters/:name')
    async getFilterObject(
        @Req() request: Request,
        @Param('name') name: string,
    ) {
        return this.honeycombService.start(request, {}, async () => {
            return this.authService.authorize(AdminPermission.APIRead, request, async (req) => {
                const filter = await this.searchFilterDAO.getFilter(this.prisma, req.corporationId, name)
                if (!filter) {
                    throw new APIError(400, { message: "Search filter not found"});
                }
                const compiled = await this.searchFilterDAO.getFullFilter(this.prisma, filter.id);
                return {
                    id: filter.id,
                    slug: filter.name,
                    displayName: filter.display_name,
                    description: filter.description,
                    compiled: basicSearchFilterWrites(compiled),
                    query: searchFilterDTOWrites(compiled)
                }
            });
        });
    }

    @Delete('/api/internal/filters/:name')
    async deleteFilterObject(
        @Req() request: Request,
        @Param('name') name: string,
    ) {
        return this.honeycombService.start(request, {}, async () => {
            return this.authService.authorize(AdminPermission.APIWrite, request, async (req) => {
                await this.searchFilterDAO.deleteFilter(this.prisma, req.corporationId, name);
                return { message: 'Success'}
            });
        });
    }
}
